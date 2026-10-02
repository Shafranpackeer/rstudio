/*
 * SessionAiChat.cpp
 *
 * Copyright (C) 2026 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#include "SessionAiChat.hpp"

#include <map>

#include <boost/bind/bind.hpp>

#include <shared_core/Error.hpp>
#include <shared_core/FilePath.hpp>
#include <shared_core/json/Json.hpp>

#include <core/Exec.hpp>
#include <core/FileSerializer.hpp>
#include <core/StringUtils.hpp>
#include <core/http/Request.hpp>
#include <core/http/Response.hpp>
#include <core/http/TcpIpAsyncClient.hpp>
#include <core/http/TcpIpAsyncClientSsl.hpp>
#include <core/http/URL.hpp>
#include <core/json/JsonRpc.hpp>
#include <core/system/Environment.hpp>

#include <session/SessionModuleContext.hpp>
#include <session/SessionServerRpc.hpp>

using namespace rstudio::core;
using namespace boost::placeholders;

namespace rstudio {
namespace session {
namespace modules {
namespace ai_chat {

namespace {

// Supported providers. "custom" is any server that speaks the OpenAI chat
// completions protocol (Ollama, LM Studio, vLLM, OpenRouter, Azure OpenAI
// proxies, and so on).
const char* const kProviderAnthropic = "anthropic";
const char* const kProviderOpenAi    = "openai";
const char* const kProviderCustom    = "custom";

const char* const kAnthropicDefaultBaseUrl = "https://api.anthropic.com";
const char* const kAnthropicDefaultModel   = "claude-sonnet-5-5";
const char* const kAnthropicVersion        = "2023-06-01";

const char* const kOpenAiDefaultBaseUrl = "https://api.openai.com/v1";
const char* const kOpenAiDefaultModel   = "gpt-5";

const char* const kCustomDefaultBaseUrl = "http://localhost:11434/v1";

const boost::posix_time::time_duration kConnectionTimeout =
   boost::posix_time::seconds(30);

// Model responses (especially ones that think, or that write whole files) can
// take a long time; cap the full request so a stalled server can't hang the
// conversation forever.
const boost::posix_time::time_duration kRequestTimeout =
   boost::posix_time::seconds(600);

struct Config
{
   std::string provider;
   std::string model;
   std::string baseUrl;

   // Saved API keys, by key slot (see keySlot()), so switching providers or
   // endpoints doesn't throw away a key the user already entered.
   std::map<std::string, std::string> apiKeys;
};

std::string defaultBaseUrl(const std::string& provider)
{
   if (provider == kProviderOpenAi)
      return kOpenAiDefaultBaseUrl;
   else if (provider == kProviderCustom)
      return kCustomDefaultBaseUrl;
   return kAnthropicDefaultBaseUrl;
}

std::string defaultModel(const std::string& provider)
{
   if (provider == kProviderOpenAi)
      return kOpenAiDefaultModel;
   else if (provider == kProviderCustom)
      return std::string();
   return kAnthropicDefaultModel;
}

bool isKnownProvider(const std::string& provider)
{
   return provider == kProviderAnthropic ||
          provider == kProviderOpenAi ||
          provider == kProviderCustom;
}

// The environment variable consulted when no key has been saved, so users
// who already export their key for other tools don't need to paste it again.
std::string environmentKeyName(const std::string& provider)
{
   if (provider == kProviderAnthropic)
      return "ANTHROPIC_API_KEY";
   else if (provider == kProviderOpenAi)
      return "OPENAI_API_KEY";
   return "RSTUDIO_AI_API_KEY";
}

std::string effectiveBaseUrl(const std::string& provider, const std::string& configuredBaseUrl)
{
   std::string baseUrl = string_utils::trimWhitespace(configuredBaseUrl);
   if (baseUrl.empty())
      baseUrl = defaultBaseUrl(provider);
   while (!baseUrl.empty() && baseUrl[baseUrl.size() - 1] == '/')
      baseUrl.erase(baseUrl.size() - 1);
   return baseUrl;
}

std::string effectiveBaseUrl(const Config& config)
{
   return effectiveBaseUrl(config.provider, config.baseUrl);
}

// Keys for Anthropic and OpenAI are saved per provider. Custom servers are
// all different services (OpenRouter, Groq, a local Ollama, ...), so their
// keys are saved per base URL. The client computes the same slots (see
// AiChatSettingsDialog) to show which endpoints already have a key.
std::string keySlot(const std::string& provider, const std::string& configuredBaseUrl)
{
   if (provider == kProviderCustom)
      return provider + "|" + effectiveBaseUrl(provider, configuredBaseUrl);
   return provider;
}

std::string keySlot(const Config& config)
{
   return keySlot(config.provider, config.baseUrl);
}

FilePath configFilePath()
{
   return module_context::userScratchPath()
         .completeChildPath("ai-chat")
         .completeChildPath("config.json");
}

Config readConfig()
{
   Config config;
   config.provider = kProviderAnthropic;

   FilePath configPath = configFilePath();
   if (configPath.exists())
   {
      std::string contents;
      Error error = readStringFromFile(configPath, &contents);
      json::Value value;
      if (!error)
         error = value.parse(contents);
      if (error)
      {
         LOG_ERROR(error);
      }
      else if (value.isObject())
      {
         const json::Object& object = value.getObject();
         json::readObject(object, "provider", config.provider);
         json::readObject(object, "model", config.model);
         json::readObject(object, "base_url", config.baseUrl);

         json::Object::Iterator it = object.find("api_keys");
         if (it != object.end() && (*it).getValue().isObject())
         {
            for (const json::Object::Member& member : (*it).getValue().getObject())
            {
               if (member.getValue().isString() && !member.getValue().getString().empty())
                  config.apiKeys[member.getName()] = member.getValue().getString();
            }
         }

         // configs written before keys were saved per provider held one key
         // for the configured provider
         std::string legacyKey;
         json::readObject(object, "api_key", legacyKey);
         if (!legacyKey.empty() && isKnownProvider(config.provider) &&
             !config.apiKeys.count(keySlot(config)))
         {
            config.apiKeys[keySlot(config)] = legacyKey;
         }
      }
   }

   if (!isKnownProvider(config.provider))
      config.provider = kProviderAnthropic;

   return config;
}

Error writeConfig(const Config& config)
{
   FilePath configPath = configFilePath();
   Error error = configPath.getParent().ensureDirectory();
   if (error)
      return error;

   json::Object keys;
   for (const auto& entry : config.apiKeys)
      keys[entry.first] = entry.second;

   json::Object object;
   object["provider"] = config.provider;
   object["model"] = config.model;
   object["base_url"] = config.baseUrl;
   object["api_keys"] = keys;

   // create the file empty and restrict it to the user before any key is
   // written into it
   if (!configPath.exists())
   {
      error = writeStringToFile(configPath, std::string());
      if (error)
         return error;
   }

#ifndef _WIN32
   error = configPath.changeFileMode(FileMode::USER_READ_WRITE);
   if (error)
      LOG_ERROR(error);
#endif

   return writeStringToFile(configPath, object.writeFormatted());
}

std::string effectiveModel(const Config& config)
{
   std::string model = string_utils::trimWhitespace(config.model);
   if (model.empty())
      model = defaultModel(config.provider);
   return model;
}

std::string effectiveApiKey(const Config& config, std::string* pSource)
{
   auto it = config.apiKeys.find(keySlot(config));
   if (it != config.apiKeys.end() && !it->second.empty())
   {
      *pSource = "saved";
      return it->second;
   }

   std::string envKey = core::system::getenv(environmentKeyName(config.provider));
   if (!envKey.empty())
   {
      *pSource = "environment";
      return envKey;
   }

   *pSource = "none";
   return std::string();
}

std::string endpointUrl(const Config& config)
{
   std::string baseUrl = effectiveBaseUrl(config);
   if (config.provider == kProviderAnthropic)
      return baseUrl + "/v1/messages";
   return baseUrl + "/chat/completions";
}

json::Object configAsJson(const Config& config)
{
   std::string keySource;
   std::string apiKey = effectiveApiKey(config, &keySource);

   // which slots hold a saved key (never the keys themselves), so the
   // settings dialog can say whether a key is needed for each choice
   json::Array savedKeySlots;
   for (const auto& entry : config.apiKeys)
      savedKeySlots.push_back(entry.first);

   json::Object result;
   result["provider"] = config.provider;
   result["model"] = effectiveModel(config);
   result["base_url"] = effectiveBaseUrl(config);
   result["has_api_key"] = !apiKey.empty();
   result["api_key_source"] = keySource;
   result["api_key_env_var"] = environmentKeyName(config.provider);
   result["saved_key_slots"] = savedKeySlots;
   return result;
}

Error aiChatGetConfig(const json::JsonRpcRequest& request,
                      json::JsonRpcResponse* pResponse)
{
   pResponse->setResult(configAsJson(readConfig()));
   return Success();
}

// Parameters: provider, model, base URL, API key. The API key is optional:
// null keeps the key saved for this provider (or custom endpoint), an empty
// string removes it, and anything else replaces it. Keys are never echoed
// back to the client.
Error aiChatSetConfig(const json::JsonRpcRequest& request,
                      json::JsonRpcResponse* pResponse)
{
   std::string provider, model, baseUrl;
   Error error = json::readParams(request.params, &provider, &model, &baseUrl);
   if (error)
      return error;

   if (!isKnownProvider(provider))
      return Error(json::errc::ParamInvalid, ERROR_LOCATION);

   Config config = readConfig();
   config.provider = provider;
   config.model = string_utils::trimWhitespace(model);
   config.baseUrl = string_utils::trimWhitespace(baseUrl);

   if (request.params.getSize() > 3 && request.params[3].isString())
   {
      std::string apiKey = string_utils::trimWhitespace(request.params[3].getString());
      if (apiKey.empty())
         config.apiKeys.erase(keySlot(config));
      else
         config.apiKeys[keySlot(config)] = apiKey;
   }

   error = writeConfig(config);
   if (error)
      return error;

   pResponse->setResult(configAsJson(config));
   return Success();
}

void resolveRequest(const json::JsonRpcFunctionContinuation& cont,
                    int status,
                    const std::string& body,
                    const std::string& errorMessage)
{
   json::Object result;
   result["status"] = status;
   result["body"] = body;
   result["error"] = errorMessage;

   json::JsonRpcResponse response;
   response.setResult(result);
   cont(Success(), &response);
}

// The async client completes on the server_rpc io thread; hop back to the
// main thread before resolving the RPC.
void onModelResponse(const json::JsonRpcFunctionContinuation& cont,
                     const http::Response& response)
{
   module_context::executeOnMainThread(
      boost::bind(resolveRequest, cont, response.statusCode(), response.body(), std::string()));
}

void onModelError(const json::JsonRpcFunctionContinuation& cont,
                  const std::string& endpoint,
                  const Error& error)
{
   std::string message = "Request to " + endpoint + " failed: " + error.getSummary();
   module_context::executeOnMainThread(
      boost::bind(resolveRequest, cont, 0, std::string(), message));
}

// Parameter: the provider-specific request body, as a JSON string. The client
// builds the body (it owns the conversation and the tool definitions); this
// side fills in the model, attaches credentials, and forwards it. The result
// is { status, body, error } -- HTTP failures are reported in the result
// rather than as RPC errors so the client can show the provider's message.
void aiChatSendRequest(const json::JsonRpcRequest& request,
                       const json::JsonRpcFunctionContinuation& cont)
{
   std::string body;
   Error error = json::readParams(request.params, &body);
   if (error)
   {
      json::JsonRpcResponse response;
      json::setErrorResponse(error, &response);
      cont(error, &response);
      return;
   }

   json::Value bodyValue;
   error = bodyValue.parse(body);
   if (error || !bodyValue.isObject())
   {
      resolveRequest(cont, 0, std::string(), "Invalid request body.");
      return;
   }

   Config config = readConfig();
   json::Object bodyObject = bodyValue.getObject();
   bodyObject["model"] = effectiveModel(config);

   std::string keySource;
   std::string apiKey = effectiveApiKey(config, &keySource);
   if (apiKey.empty() && config.provider != kProviderCustom)
   {
      resolveRequest(cont, 0, std::string(),
                     "No API key is configured. Open the AI pane settings to add one, "
                     "or set the " + environmentKeyName(config.provider) +
                     " environment variable.");
      return;
   }

   std::string endpoint = endpointUrl(config);
   http::URL url(endpoint);
   bool useSsl = url.protocol() == "https";
   if (!url.isValid() || (!useSsl && url.protocol() != "http"))
   {
      resolveRequest(cont, 0, std::string(),
                     "The configured base URL is not a valid http(s) URL: " + endpoint);
      return;
   }

   http::Request httpRequest;
   httpRequest.setMethod("POST");
   httpRequest.setUri(url.path());
   httpRequest.setHost(url.host());
   httpRequest.setHeader("Connection", "close");
   httpRequest.setHeader("Accept", "application/json");
   httpRequest.setContentType("application/json");
   if (config.provider == kProviderAnthropic)
   {
      httpRequest.setHeader("x-api-key", apiKey);
      httpRequest.setHeader("anthropic-version", kAnthropicVersion);
   }
   else if (!apiKey.empty())
   {
      httpRequest.setHeader("Authorization", "Bearer " + apiKey);
   }
   httpRequest.setBody(bodyObject.write());

   boost::shared_ptr<http::IAsyncClient> pClient;
   if (useSsl)
   {
      pClient.reset(new http::TcpIpAsyncClientSsl(server_rpc::ioContext(),
                                                  url.hostname(),
                                                  url.portStr(),
                                                  true, // verify certificates
                                                  std::string(),
                                                  kConnectionTimeout));
   }
   else
   {
      pClient.reset(new http::TcpIpAsyncClient(server_rpc::ioContext(),
                                               url.hostname(),
                                               url.portStr(),
                                               kConnectionTimeout));
   }

   pClient->request().assign(httpRequest);
   pClient->setRequestTimeout(kRequestTimeout);
   pClient->execute(
      boost::bind(onModelResponse, cont, _1),
      boost::bind(onModelError, cont, endpoint, _1));
}

} // anonymous namespace

Error initialize()
{
   using boost::bind;
   using namespace module_context;

   ExecBlock initBlock;
   initBlock.addFunctions()
      (bind(registerRpcMethod, "ai_chat_get_config", aiChatGetConfig))
      (bind(registerRpcMethod, "ai_chat_set_config", aiChatSetConfig))
      (bind(registerAsyncRpcMethod, "ai_chat_send_request", aiChatSendRequest))
      (bind(sourceModuleRFile, "SessionAiChat.R"));

   return initBlock.execute();
}

} // namespace ai_chat
} // namespace modules
} // namespace session
} // namespace rstudio
