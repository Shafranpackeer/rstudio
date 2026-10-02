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

   @DefaultMessage("Custom providers can be any server with an OpenAI-compatible /chat/completions endpoint, such as Ollama (http://localhost:11434/v1), LM Studio, vLLM, or OpenRouter. The model must support tool calling.")
   String customHelp();

   @DefaultMessage("The API key is stored only in your RStudio data directory and is never sent anywhere except the provider you choose.")
   String keyStorageHelp();

   @DefaultMessage("A model name is required for custom providers.")
   String modelRequired();

   @DefaultMessage("Error Saving AI Settings")
   String errorSavingSettings();
}
