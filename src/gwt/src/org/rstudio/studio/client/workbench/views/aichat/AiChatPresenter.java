/*
 * AiChatPresenter.java
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

import org.rstudio.core.client.FilePosition;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.CommandWithArg;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.WorkbenchContext;
import org.rstudio.studio.client.workbench.WorkbenchView;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.BasePresenter;
import org.rstudio.studio.client.workbench.views.aichat.model.AiChatConfig;
import org.rstudio.studio.client.workbench.views.aichat.model.AiChatHttpResult;
import org.rstudio.studio.client.workbench.views.aichat.model.AiChatProtocol;
import org.rstudio.studio.client.workbench.views.aichat.model.AiChatServerOperations;
import org.rstudio.studio.client.workbench.views.source.SourceColumn;
import org.rstudio.studio.client.workbench.views.source.SourceColumnManager;
import org.rstudio.studio.client.workbench.views.source.editors.EditingTarget;
import org.rstudio.studio.client.workbench.views.source.editors.text.DocDisplay;
import org.rstudio.studio.client.workbench.views.source.editors.text.TextEditingTarget;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * The AI pane: a chat with a model the user brings (Anthropic, OpenAI, or any
 * OpenAI-compatible server) that can use tools to read and change the user's
 * files, editor, and R session.
 *
 * The agent loop lives here: send the conversation, show the reply, run any
 * tool calls it contains (asking the user first for anything that changes
 * state), append the results, and repeat until the model answers without
 * calling a tool.
 */
public class AiChatPresenter extends BasePresenter
{
   public interface Display extends WorkbenchView
   {
      interface Observer
      {
         void onSend(String text);
         void onStop();
         void onNewChat();
         void onShowSettings();
         void onInsertCode(String code);
      }

      interface ToolCallView
      {
         void requestApproval(Command onApprove, Command onDeny);
         void setRunning();
         void setResult(String result, boolean success);
         void setDenied();

         /** Jev's risk check is in progress. */
         void setChecking();

         /** Shows Jev's verdict; flagged verdicts are highlighted. */
         void setRiskAssessment(String text, boolean flagged);
      }

      void setObserver(Observer observer);
      void setStatus(String status);
      void setBusy(boolean busy);
      void clearTranscript();
      void showWelcome(boolean configured);
      void addUserMessage(String text);
      void addAssistantMessage(String markdown);
      void addErrorMessage(String text);
      void addInfoMessage(String text);
      ToolCallView addToolCall(String name, String summary, String input);
      boolean isAutoApprove();
      void focusInput();
   }

   @Inject
   public AiChatPresenter(Display display,
                          AiChatServerOperations server,
                          GlobalDisplay globalDisplay,
                          Commands commands,
                          WorkbenchContext workbenchContext,
                          FileTypeRegistry fileTypeRegistry,
                          Provider<SourceColumnManager> pSourceColumnManager)
   {
      super(display);
      display_ = display;
      server_ = server;
      globalDisplay_ = globalDisplay;
      commands_ = commands;
      workbenchContext_ = workbenchContext;
      fileTypeRegistry_ = fileTypeRegistry;
      pSourceColumnManager_ = pSourceColumnManager;

      messages_ = JavaScriptObject.createArray().cast();

      display_.setObserver(new Display.Observer()
      {
         @Override
         public void onSend(String text) { send(text); }

         @Override
         public void onStop() { stop(); }

         @Override
         public void onNewChat() { newChat(); }

         @Override
         public void onShowSettings() { showSettings(); }

         @Override
         public void onInsertCode(String code) { insertCode(code); }
      });
      display_.showWelcome(true);
      refreshConfig(null);
   }

   // User actions -----------------------------------------------------------

   private void send(String text)
   {
      if (busy_)
         return;

      if (config_ == null)
      {
         refreshConfig(() -> send(text));
         return;
      }

      if (!isConfigured())
      {
         showSettings();
         return;
      }

      display_.addUserMessage(text);
      messages_.push(AiChatProtocol.userMessage(text));

      steps_ = 0;
      setBusy(true);
      requestReply(generation_);
   }

   private void stop()
   {
      if (!busy_)
         return;

      // invalidate any request or tool call in flight; the protocol layer
      // fills in results for tool calls that never ran
      generation_++;
      if (pendingApproval_ != null)
      {
         pendingApproval_.setDenied();
         pendingApproval_ = null;
      }
      display_.addInfoMessage(constants_.stopped());
      setBusy(false);
   }

   private void newChat()
   {
      generation_++;
      pendingApproval_ = null;
      messages_ = JavaScriptObject.createArray().cast();
      setBusy(false);
      display_.showWelcome(config_ == null || isConfigured());
      display_.focusInput();
   }

   private void showSettings()
   {
      if (config_ == null)
      {
         refreshConfig(this::showSettings);
         return;
      }

      new AiChatSettingsDialog(config_, result ->
      {
         server_.aiChatSetConfig(
               result.provider,
               result.model,
               result.baseUrl,
               result.apiKey,
               result.jevEnabled,
               result.jevApiKey,
               new ServerRequestCallback<AiChatConfig>()
               {
                  @Override
                  public void onResponseReceived(AiChatConfig config)
                  {
                     setConfig(config);
                     if (messages_.length() == 0)
                        display_.showWelcome(isConfigured());
                     if (isConfigured())
                        testConnection();
                     display_.focusInput();
                  }

                  @Override
                  public void onError(ServerError error)
                  {
                     globalDisplay_.showErrorMessage(
                           constants_.errorSavingSettings(), error.getUserMessage());
                  }
               });
      }).showModal();
   }

   private void insertCode(String code)
   {
      String error = insertIntoEditor(code);
      if (error != null)
         display_.addErrorMessage(error);
   }

   // Configuration ----------------------------------------------------------

   private void refreshConfig(Command onLoaded)
   {
      server_.aiChatGetConfig(new ServerRequestCallback<AiChatConfig>()
      {
         @Override
         public void onResponseReceived(AiChatConfig config)
         {
            setConfig(config);
            if (messages_.length() == 0)
               display_.showWelcome(isConfigured());
            if (onLoaded != null)
               onLoaded.execute();
         }

         @Override
         public void onError(ServerError error)
         {
            display_.setStatus(error.getUserMessage());
         }
      });
   }

   private void setConfig(AiChatConfig config)
   {
      config_ = config;
      updateStatus();
   }

   private void updateStatus()
   {
      if (!isConfigured())
      {
         display_.setStatus(constants_.notConfigured());
         return;
      }

      String status = constants_.connectedTo(providerName(config_), config_.getModel());
      if (!toolsSupported())
         status += " \u2013 " + constants_.chatOnly(); //$NON-NLS-1$
      display_.setStatus(status);
   }

   // Models without tool calling are remembered per endpoint and model, so
   // switching to a capable model turns the tools back on.
   private String modelKey()
   {
      return config_.getProvider() + "|" + config_.getBaseUrl() + "|" + config_.getModel(); //$NON-NLS-1$ //$NON-NLS-2$
   }

   private boolean toolsSupported()
   {
      return config_ == null || !StringUtil.equals(toolsUnsupportedFor_, modelKey());
   }

   private void markToolsUnsupported()
   {
      toolsUnsupportedFor_ = modelKey();
      updateStatus();
      display_.addInfoMessage(constants_.toolsUnsupported(config_.getModel()));
   }

   /** True when this error means the model can't do tool calling, and we
    *  haven't already switched it to chat-only mode. */
   private boolean shouldFallBackToChatOnly(String error)
   {
      return StringUtil.equals(config_.getProvider(), AiChatConfig.PROVIDER_CUSTOM) &&
             toolsSupported() &&
             AiChatProtocol.isToolsUnsupportedError(error);
   }

   /**
    * Sends a tiny request with the current settings so problems (a bad key,
    * a wrong address, a model without tool calling) show up right away
    * rather than on the user's first real message.
    */
   private void testConnection()
   {
      if (busy_)
         return;

      final int generation = ++generation_;
      final String provider = config_.getProvider();
      setBusy(true);
      display_.addInfoMessage(constants_.testingConnection());

      JsArray<JavaScriptObject> probe = JavaScriptObject.createArray().cast();
      probe.push(AiChatProtocol.userMessage("Reply with just the word OK.")); //$NON-NLS-1$

      sendProbe(provider, probe, toolsSupported(), generation);
   }

   private void sendProbe(final String provider,
                          final JsArray<JavaScriptObject> probe,
                          final boolean includeTools,
                          final int generation)
   {
      String body = AiChatProtocol.buildRequestBody(provider, systemPrompt(), probe, includeTools);
      server_.aiChatSendRequest(body, new ServerRequestCallback<AiChatHttpResult>()
      {
         @Override
         public void onResponseReceived(AiChatHttpResult result)
         {
            if (generation != generation_)
               return;

            String error = result.getError();
            if (StringUtil.isNullOrEmpty(error))
            {
               JavaScriptObject reply = AiChatProtocol.parseResponse(
                     provider, result.getStatus(), result.getBody());
               error = AiChatProtocol.getError(reply);
            }

            if (error == null || error.isEmpty())
            {
               if (includeTools)
                  display_.addInfoMessage(constants_.connectionOk(config_.getModel()));
               setBusy(false);
            }
            else if (includeTools && shouldFallBackToChatOnly(error))
            {
               markToolsUnsupported();
               sendProbe(provider, probe, false, generation);
            }
            else
            {
               display_.addErrorMessage(constants_.connectionFailed(error));
               setBusy(false);
            }
         }

         @Override
         public void onError(ServerError error)
         {
            if (generation != generation_)
               return;
            display_.addErrorMessage(constants_.connectionFailed(error.getUserMessage()));
            setBusy(false);
         }
      });
   }

   private boolean isConfigured()
   {
      // custom (often local) servers may not need a key at all
      return config_ != null &&
             (config_.hasApiKey() ||
              StringUtil.equals(config_.getProvider(), AiChatConfig.PROVIDER_CUSTOM));
   }

   private String providerName(AiChatConfig config)
   {
      String provider = config.getProvider();
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_OPENAI))
         return "OpenAI"; //$NON-NLS-1$
      if (StringUtil.equals(provider, AiChatConfig.PROVIDER_CUSTOM))
      {
         // name the service by its host, e.g. "openrouter.ai" or "localhost:11434"
         String host = config.getBaseUrl().replaceFirst("^[a-zA-Z]+://", "").replaceFirst("/.*$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
         return host.isEmpty() ? constants_.providerCustom() : host;
      }
      return "Anthropic"; //$NON-NLS-1$
   }

   // Agent loop -------------------------------------------------------------

   private void setBusy(boolean busy)
   {
      busy_ = busy;
      display_.setBusy(busy);
      if (!busy)
         display_.focusInput();
   }

   private void requestReply(final int generation)
   {
      if (generation != generation_)
         return;

      if (++steps_ > MAX_STEPS)
      {
         display_.addInfoMessage(constants_.tooManySteps(MAX_STEPS));
         setBusy(false);
         return;
      }

      final String provider = config_.getProvider();
      final boolean includeTools = toolsSupported();
      String body = AiChatProtocol.buildRequestBody(provider, systemPrompt(), messages_, includeTools);

      server_.aiChatSendRequest(body, new ServerRequestCallback<AiChatHttpResult>()
      {
         @Override
         public void onResponseReceived(AiChatHttpResult result)
         {
            if (generation != generation_)
               return;

            if (!StringUtil.isNullOrEmpty(result.getError()))
            {
               fail(result.getError());
               return;
            }

            JavaScriptObject reply = AiChatProtocol.parseResponse(
                  provider, result.getStatus(), result.getBody());
            String error = AiChatProtocol.getError(reply);
            if (error != null)
            {
               // the model can't call tools: carry on as a plain chat
               if (includeTools && shouldFallBackToChatOnly(error))
               {
                  markToolsUnsupported();
                  steps_--;
                  requestReply(generation);
                  return;
               }

               fail(error);
               return;
            }

            messages_.push(reply);

            String content = AiChatProtocol.getContent(reply);
            if (!content.trim().isEmpty())
               display_.addAssistantMessage(content);

            JsArray<JavaScriptObject> calls = AiChatProtocol.getToolCalls(reply);
            if (calls.length() == 0)
               setBusy(false);
            else
               runToolCalls(calls, 0, generation);
         }

         @Override
         public void onError(ServerError error)
         {
            if (generation == generation_)
               fail(error.getUserMessage());
         }
      });
   }

   private void fail(String message)
   {
      display_.addErrorMessage(message);
      setBusy(false);
   }

   private void runToolCalls(final JsArray<JavaScriptObject> calls,
                             final int index,
                             final int generation)
   {
      if (generation != generation_)
         return;

      // every call has a result; let the model continue
      if (index >= calls.length())
      {
         requestReply(generation);
         return;
      }

      final JavaScriptObject call = calls.get(index);
      final String name = AiChatProtocol.getToolCallName(call);
      final JavaScriptObject input = AiChatProtocol.getToolCallInput(call);
      final Display.ToolCallView view = display_.addToolCall(
            name, summarize(name, input), AiChatProtocol.formatInput(input));

      final Command next = () -> runToolCalls(calls, index + 1, generation);

      if (!AiChatProtocol.requiresApproval(name))
      {
         view.setRunning();
         executeTool(call, view, next, generation);
         return;
      }

      // with the Jev safety check on, rate the action first; flagged actions
      // always ask, even with auto-approve on
      if (config_ != null && config_.isJevEnabled())
      {
         view.setChecking();
         checkWithJev(name, input, view, generation, (flagged) ->
            approveOrRun(call, view, next, generation, flagged));
         return;
      }

      approveOrRun(call, view, next, generation, false);
   }

   private void approveOrRun(final JavaScriptObject call,
                             final Display.ToolCallView view,
                             final Command next,
                             final int generation,
                             boolean mustAsk)
   {
      if (generation != generation_)
         return;

      if (mustAsk || !display_.isAutoApprove())
      {
         pendingApproval_ = view;
         view.requestApproval(
               () ->
               {
                  pendingApproval_ = null;
                  if (generation != generation_)
                     return;
                  view.setRunning();
                  executeTool(call, view, next, generation);
               },
               () ->
               {
                  pendingApproval_ = null;
                  if (generation != generation_)
                     return;
                  view.setDenied();
                  addToolResult(call, constants_.userDenied());
                  next.execute();
               });
      }
      else
      {
         view.setRunning();
         executeTool(call, view, next, generation);
      }
   }

   /**
    * Asks Jev to rate an action's risk, shows the verdict on the tool card,
    * and calls back with whether the action must be approved by hand. When
    * Jev can't be reached the action is treated as needing approval.
    */
   private void checkWithJev(String name,
                             JavaScriptObject input,
                             final Display.ToolCallView view,
                             final int generation,
                             final CommandWithArg<Boolean> onChecked)
   {
      String body = AiChatProtocol.buildJevRiskRequest(name, input);
      server_.aiChatJevRequest(body, new ServerRequestCallback<AiChatHttpResult>()
      {
         @Override
         public void onResponseReceived(AiChatHttpResult result)
         {
            if (generation != generation_)
               return;

            String error = result.getError();
            JavaScriptObject parsed = null;
            if (StringUtil.isNullOrEmpty(error))
            {
               parsed = AiChatProtocol.parseJevRisks(result.getStatus(), result.getBody());
               error = AiChatProtocol.getError(parsed);
            }

            if (!StringUtil.isNullOrEmpty(error))
            {
               view.setRiskAssessment(constants_.jevUnavailable(error), true);
               onChecked.execute(true);
               return;
            }

            JsArray<JavaScriptObject> flagged =
                  AiChatProtocol.getFlaggedRisks(parsed, JEV_RISK_THRESHOLD);
            if (flagged.length() == 0)
            {
               view.setRiskAssessment(constants_.jevLowRisk(), false);
               onChecked.execute(false);
               return;
            }

            StringBuilder risks = new StringBuilder();
            for (int i = 0; i < flagged.length(); i++)
            {
               if (i > 0)
                  risks.append(", "); //$NON-NLS-1$
               JavaScriptObject risk = flagged.get(i);
               risks.append(riskLabel(AiChatProtocol.getRiskId(risk)))
                    .append(" (") //$NON-NLS-1$
                    .append(Math.round(AiChatProtocol.getRiskProbability(risk) * 100))
                    .append("%)"); //$NON-NLS-1$
            }
            view.setRiskAssessment(constants_.jevFlagged(risks.toString()), true);
            onChecked.execute(true);
         }

         @Override
         public void onError(ServerError error)
         {
            if (generation != generation_)
               return;
            view.setRiskAssessment(constants_.jevUnavailable(error.getUserMessage()), true);
            onChecked.execute(true);
         }
      });
   }

   private String riskLabel(String id)
   {
      if (StringUtil.equals(id, "deletes_data")) //$NON-NLS-1$
         return constants_.riskDeletesData();
      if (StringUtil.equals(id, "installs_software")) //$NON-NLS-1$
         return constants_.riskInstallsSoftware();
      if (StringUtil.equals(id, "uses_network")) //$NON-NLS-1$
         return constants_.riskUsesNetwork();
      if (StringUtil.equals(id, "runs_system_commands")) //$NON-NLS-1$
         return constants_.riskRunsSystemCommands();
      return id;
   }

   private void executeTool(final JavaScriptObject call,
                            final Display.ToolCallView view,
                            final Command next,
                            final int generation)
   {
      final String name = AiChatProtocol.getToolCallName(call);
      final JavaScriptObject input = AiChatProtocol.getToolCallInput(call);

      if (AiChatProtocol.isClientTool(name))
      {
         String result;
         boolean success = true;
         try
         {
            result = executeClientTool(name, input);
         }
         catch (Exception e)
         {
            result = "Error: " + e.getMessage(); //$NON-NLS-1$
            success = false;
         }

         view.setResult(result, success && !result.startsWith("Error:")); //$NON-NLS-1$
         addToolResult(call, result);
         next.execute();
         return;
      }

      server_.aiChatExecuteTool(name, input, new ServerRequestCallback<String>()
      {
         @Override
         public void onResponseReceived(String result)
         {
            if (generation != generation_)
               return;

            String value = StringUtil.notNull(result);
            view.setResult(value, !value.startsWith("Error:")); //$NON-NLS-1$
            addToolResult(call, value);

            // running code can create or change objects in the session
            if (StringUtil.equals(name, AiChatProtocol.TOOL_RUN_R_CODE))
               commands_.refreshEnvironment().execute();

            next.execute();
         }

         @Override
         public void onError(ServerError error)
         {
            if (generation != generation_)
               return;

            String value = "Error: " + error.getUserMessage(); //$NON-NLS-1$
            view.setResult(value, false);
            addToolResult(call, value);
            next.execute();
         }
      });
   }

   private void addToolResult(JavaScriptObject call, String result)
   {
      messages_.push(AiChatProtocol.toolResultMessage(
            AiChatProtocol.getToolCallId(call),
            AiChatProtocol.getToolCallName(call),
            result));
   }

   private String summarize(String name, JavaScriptObject input)
   {
      String code = AiChatProtocol.getStringArg(input, "code"); //$NON-NLS-1$
      if (code != null)
      {
         String firstLine = code.trim().split("\n")[0]; //$NON-NLS-1$
         return code.trim().contains("\n") ? firstLine + " ..." : firstLine; //$NON-NLS-1$
      }

      String path = AiChatProtocol.getStringArg(input, "path"); //$NON-NLS-1$
      if (path != null)
         return path;

      return "";
   }

   // Client-side tools ------------------------------------------------------

   private String executeClientTool(String name, JavaScriptObject input)
   {
      if (StringUtil.equals(name, AiChatProtocol.TOOL_GET_ACTIVE_DOCUMENT))
         return describeActiveDocument();

      if (StringUtil.equals(name, AiChatProtocol.TOOL_OPEN_FILE))
      {
         String path = AiChatProtocol.getStringArg(input, "path"); //$NON-NLS-1$
         if (StringUtil.isNullOrEmpty(path))
            return "Error: 'path' is required."; //$NON-NLS-1$

         String resolved = resolvePath(path);
         int line = AiChatProtocol.getIntArg(input, "line"); //$NON-NLS-1$
         FileSystemItem file = FileSystemItem.createFile(resolved);
         if (line > 0)
            fileTypeRegistry_.editFile(file, FilePosition.create(line, 0));
         else
            fileTypeRegistry_.editFile(file);
         return "Opened " + resolved + " in the editor."; //$NON-NLS-1$ //$NON-NLS-2$
      }

      if (StringUtil.equals(name, AiChatProtocol.TOOL_INSERT_TEXT))
      {
         String text = AiChatProtocol.getStringArg(input, "text"); //$NON-NLS-1$
         if (text == null)
            return "Error: 'text' is required."; //$NON-NLS-1$

         String error = insertIntoEditor(text);
         return error == null ? "Inserted the text into the active document." : "Error: " + error; //$NON-NLS-1$ //$NON-NLS-2$
      }

      return "Error: unknown tool '" + name + "'"; //$NON-NLS-1$ //$NON-NLS-2$
   }

   private TextEditingTarget activeTextEditor()
   {
      SourceColumnManager columns = pSourceColumnManager_.get();
      if (!columns.hasActiveEditor())
         return null;

      EditingTarget target = columns.getActive().getActiveEditor();
      return target instanceof TextEditingTarget ? (TextEditingTarget) target : null;
   }

   /** Inserts text into the active editor; returns an error message on failure. */
   private String insertIntoEditor(String text)
   {
      TextEditingTarget editor = activeTextEditor();
      if (editor == null)
         return "No text document is open in the source editor."; //$NON-NLS-1$

      DocDisplay docDisplay = editor.getDocDisplay();
      docDisplay.replaceSelection(text);
      docDisplay.focus();
      return null;
   }

   private String describeActiveDocument()
   {
      StringBuilder out = new StringBuilder();
      SourceColumnManager columns = pSourceColumnManager_.get();

      EditingTarget active = columns.hasActiveEditor()
            ? columns.getActive().getActiveEditor()
            : null;

      if (active == null)
      {
         out.append("No document is open in the source editor.\n"); //$NON-NLS-1$
      }
      else
      {
         String path = active.getPath();
         Boolean dirty = active.dirtyState().getValue();

         out.append("Active document: ") //$NON-NLS-1$
            .append(StringUtil.isNullOrEmpty(path) ? active.getName().getValue() + " (untitled, not saved to disk)" : path) //$NON-NLS-1$
            .append("\n"); //$NON-NLS-1$
         out.append("Unsaved changes: ").append(dirty != null && dirty ? "yes" : "no").append("\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

         if (active instanceof TextEditingTarget)
         {
            DocDisplay docDisplay = ((TextEditingTarget) active).getDocDisplay();
            Position cursor = docDisplay.getCursorPosition();
            out.append("Cursor: line ").append(cursor.getRow() + 1) //$NON-NLS-1$
               .append(", column ").append(cursor.getColumn() + 1).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$

            String selection = docDisplay.getSelectionValue();
            if (!StringUtil.isNullOrEmpty(selection))
               out.append("Selected text:\n").append(selection).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$

            String[] lines = docDisplay.getCode().split("\n", -1); //$NON-NLS-1$
            out.append("Contents (").append(lines.length).append(" lines; line numbers are not part of the file):\n"); //$NON-NLS-1$ //$NON-NLS-2$
            int limit = Math.min(lines.length, MAX_DOCUMENT_LINES);
            for (int i = 0; i < limit; i++)
               out.append(i + 1).append("  ").append(lines[i]).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
            if (lines.length > limit)
               out.append("... (").append(lines.length - limit).append(" more lines; use read_file to see them)\n"); //$NON-NLS-1$ //$NON-NLS-2$
         }
      }

      StringBuilder others = new StringBuilder();
      for (SourceColumn column : columns.getColumnList())
      {
         for (EditingTarget target : column.getEditors())
         {
            if (target == active)
               continue;
            String path = target.getPath();
            others.append("  ") //$NON-NLS-1$
                  .append(StringUtil.isNullOrEmpty(path) ? target.getName().getValue() : path)
                  .append("\n"); //$NON-NLS-1$
         }
      }
      if (others.length() > 0)
         out.append("Other open documents:\n").append(others); //$NON-NLS-1$

      return out.toString();
   }

   private String resolvePath(String path)
   {
      boolean absolute = path.startsWith("/") || //$NON-NLS-1$
                         path.startsWith("~") || //$NON-NLS-1$
                         path.matches("^[A-Za-z]:[/\\\\].*"); //$NON-NLS-1$
      if (absolute)
         return path;

      String cwd = workbenchContext_.getCurrentWorkingDir().getPath();
      if (path.startsWith("./")) //$NON-NLS-1$
         path = path.substring(2);
      return cwd.endsWith("/") ? cwd + path : cwd + "/" + path; //$NON-NLS-1$ //$NON-NLS-2$
   }

   private String systemPrompt()
   {
      String cwd = workbenchContext_.getCurrentWorkingDir() != null
            ? workbenchContext_.getCurrentWorkingDir().getPath()
            : "~"; //$NON-NLS-1$

      if (!toolsSupported())
      {
         return
            "You are an AI coding assistant built into RStudio, the IDE for R (and Python). " +
            "You help the user understand and write code and analyze data. In this mode you " +
            "cannot see the user's files or R session, so ask them to paste the code or " +
            "output you need, and give complete code they can run or insert. Be concise. " +
            "Use Markdown, and tag fenced code blocks with their language.\n\n" +
            "The R working directory is " + cwd + "."; //$NON-NLS-1$
      }

      return
         "You are an AI coding assistant built into RStudio, the IDE for R (and Python). " +
         "You help the user understand, write, and change code, analyze data, and work in " +
         "their R session.\n\n" +
         "You can act inside the IDE with tools: inspect the R session (get_ide_context), " +
         "see the document the user is looking at (get_active_document), browse and read " +
         "files (list_files, read_file), change files (edit_file, write_file), run R code " +
         "in the user's session (run_r_code), open files in the editor (open_file), and " +
         "insert text into the active document (insert_text_in_editor).\n\n" +
         "Guidelines:\n" +
         "- When the user refers to \"this code\", \"my file\", or a selection, call " +
         "get_active_document first.\n" +
         "- Read a file before you change it. Use edit_file for targeted changes and " +
         "write_file for new files or complete rewrites.\n" +
         "- Files changed on disk reload in the editor automatically unless they have " +
         "unsaved changes. If the active document has unsaved changes or is untitled, use " +
         "insert_text_in_editor, or ask the user to save first.\n" +
         "- Actions that change files, the editor, or the R session may require the user's " +
         "approval. If the user denies an action, do not retry it; ask how to proceed.\n" +
         "- Do not run code that deletes data or files, installs software, or makes " +
         "network requests unless the user asked for it.\n" +
         "- Be concise. Use Markdown, and tag fenced code blocks with their language.\n\n" +
         "The R working directory is " + cwd + "."; //$NON-NLS-1$
   }

   private static final int MAX_STEPS = 25;

   // a Jev "yes" probability at or above this flags the action as risky
   private static final double JEV_RISK_THRESHOLD = 0.5;
   private static final int MAX_DOCUMENT_LINES = 2000;

   private final Display display_;
   private final AiChatServerOperations server_;
   private final GlobalDisplay globalDisplay_;
   private final Commands commands_;
   private final WorkbenchContext workbenchContext_;
   private final FileTypeRegistry fileTypeRegistry_;
   private final Provider<SourceColumnManager> pSourceColumnManager_;

   private AiChatConfig config_;

   // the model (see modelKey()) found not to support tool calling, if any
   private String toolsUnsupportedFor_;
   private JsArray<JavaScriptObject> messages_;
   private Display.ToolCallView pendingApproval_;
   private boolean busy_;
   private int steps_;

   // incremented to abandon in-flight work (stop, new chat)
   private int generation_;

   private static final AiChatConstants constants_ = GWT.create(AiChatConstants.class);
}
