/*
 * AiChatConstants.java
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
package org.rstudio.studio.client.workbench.views.aichat;

public interface AiChatConstants extends com.google.gwt.i18n.client.Messages
{
   @DefaultMessage("AI")
   String aiChatTitle();

   @DefaultMessage("AI Tab")
   String aiChatTabLabel();

   @DefaultMessage("New Chat")
   String newChat();

   @DefaultMessage("AI Settings")
   String settings();

   @DefaultMessage("Send")
   String send();

   @DefaultMessage("Stop")
   String stop();

   @DefaultMessage("Auto-approve actions")
   String autoApprove();

   @DefaultMessage("Run file edits and R code without asking first")
   String autoApproveTitle();

   @DefaultMessage("Ask the AI to explain, write, or change code. Enter to send, Shift+Enter for a new line.")
   String inputPlaceholder();

   @DefaultMessage("Message to the AI")
   String inputLabel();

   @DefaultMessage("Chat with your own AI")
   String welcomeTitle();

   @DefaultMessage("Connect Claude, ChatGPT, or any OpenAI-compatible model (such as a local Ollama server). The AI can read your files, look at the open document, edit code, and run R code in your session. Anything that changes your files or session asks for your approval first.")
   String welcomeMessage();

   @DefaultMessage("Using a custom or local model? It needs tool calling (function calling) support to read files, edit code, or run R. Without it, the AI works in chat-only mode.")
   String welcomeToolsNote();

   @DefaultMessage("Configure AI Provider...")
   String configureButton();

   @DefaultMessage("Connected to {0} ({1})")
   String connectedTo(String provider, String model);

   @DefaultMessage("Not configured. Add an API key in settings.")
   String notConfigured();

   @DefaultMessage("You")
   String userLabel();

   @DefaultMessage("Thinking...")
   String thinking();

   @DefaultMessage("Approve")
   String approve();

   @DefaultMessage("Deny")
   String deny();

   @DefaultMessage("Waiting for approval")
   String waitingForApproval();

   @DefaultMessage("Running...")
   String running();

   @DefaultMessage("Done")
   String done();

   @DefaultMessage("Denied")
   String denied();

   @DefaultMessage("Failed")
   String failed();

   @DefaultMessage("Show details")
   String showDetails();

   @DefaultMessage("Hide details")
   String hideDetails();

   @DefaultMessage("Result")
   String toolResult();

   @DefaultMessage("Copy")
   String copy();

   @DefaultMessage("Insert")
   String insert();

   @DefaultMessage("Insert into the active editor")
   String insertTitle();

   @DefaultMessage("Error")
   String error();

   @DefaultMessage("The user denied this action.")
   String userDenied();

   @DefaultMessage("Stopped.")
   String stopped();

   @DefaultMessage("Stopped after {0} consecutive tool calls. Send a message to continue.")
   String tooManySteps(int count);

   // Settings dialog

   @DefaultMessage("AI Provider")
   String settingsCaption();

   @DefaultMessage("Provider:")
   String providerLabel();

   @DefaultMessage("Anthropic (Claude)")
   String providerAnthropic();

   @DefaultMessage("OpenAI (ChatGPT)")
   String providerOpenAi();

   @DefaultMessage("Custom (OpenAI-compatible)")
   String providerCustom();

   @DefaultMessage("Model:")
   String modelLabel();

   @DefaultMessage("Base URL:")
   String baseUrlLabel();

   @DefaultMessage("API key:")
   String apiKeyLabel();

   @DefaultMessage("A key is saved. Leave blank to keep it.")
   String apiKeySaved();

   @DefaultMessage("Using the {0} environment variable. Enter a key to override it.")
   String apiKeyFromEnvironment(String envVar);

   @DefaultMessage("No key yet. Enter one, or set the {0} environment variable.")
   String apiKeyMissing(String envVar);

   @DefaultMessage("Remove saved API key")
   String removeApiKey();

   @DefaultMessage("Pick a service to fill in its address, or choose Other for any server with an OpenAI-compatible /chat/completions endpoint (vLLM, LiteLLM, a company gateway, and so on). Paste that service''s API key below.")
   String customHelp();

   @DefaultMessage("In Jan, open Settings > Local API Server and click Start Server. If you set an API key there, enter the same key below.")
   String janNote();

   // Jev safety check

   @DefaultMessage("Safety check with Jev (TypeSafe AI)")
   String jevSectionLabel();

   @DefaultMessage("Check file edits and R code with Jev before they run")
   String jevEnable();

   @DefaultMessage("Jev is a fast decision model that rates each action for risk: deleting or overwriting data, installing software, network access, and system commands. Risky actions are flagged and always ask for approval, even with Auto-approve on. Uses your own TypeSafe API key; the action''s details are sent to TypeSafe for the check.")
   String jevHelp();

   @DefaultMessage("TypeSafe API key:")
   String jevApiKeyLabel();

   @DefaultMessage("Remove saved TypeSafe API key")
   String jevRemoveApiKey();

   @DefaultMessage("A TypeSafe API key is required to turn on the Jev safety check.")
   String jevKeyRequired();

   @DefaultMessage("Checking with Jev...")
   String jevChecking();

   @DefaultMessage("Jev: low risk")
   String jevLowRisk();

   @DefaultMessage("Jev flagged: {0}")
   String jevFlagged(String risks);

   @DefaultMessage("Jev check unavailable ({0}); asking for approval.")
   String jevUnavailable(String error);

   @DefaultMessage("may delete or overwrite data")
   String riskDeletesData();

   @DefaultMessage("may install or remove software")
   String riskInstallsSoftware();

   @DefaultMessage("may use the network")
   String riskUsesNetwork();

   @DefaultMessage("may run system commands")
   String riskRunsSystemCommands();

   @DefaultMessage("Service:")
   String serviceLabel();

   @DefaultMessage("Other OpenAI-compatible server")
   String serviceOther();

   @DefaultMessage("API key (optional for local servers):")
   String apiKeyOptionalLabel();

   @DefaultMessage("Important: the model must support tool calling (also called function calling) for the AI to read your files, edit code, or run R. Models without it can still chat, and RStudio switches to chat-only mode automatically when it detects this.")
   String toolCallingHelp();

   @DefaultMessage("Chat only: {0} does not support tool calling, so the AI can answer questions but cannot read files, edit code, or run R. Choose a model that supports tool calling to enable those.")
   String toolsUnsupported(String model);

   @DefaultMessage("chat only")
   String chatOnly();

   @DefaultMessage("Testing connection...")
   String testingConnection();

   @DefaultMessage("Connected to {0}. The AI is ready.")
   String connectionOk(String model);

   @DefaultMessage("Could not connect: {0}")
   String connectionFailed(String error);

   @DefaultMessage("The API key is stored only in your RStudio data directory and is never sent anywhere except the provider you choose.")
   String keyStorageHelp();

   @DefaultMessage("A model name is required for custom providers.")
   String modelRequired();

   @DefaultMessage("Error Saving AI Settings")
   String errorSavingSettings();
}
