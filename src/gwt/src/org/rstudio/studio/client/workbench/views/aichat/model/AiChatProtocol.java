/*
 * AiChatProtocol.java
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
package org.rstudio.studio.client.workbench.views.aichat.model;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

/**
 * Translates between the AI pane's provider-neutral conversation and the wire
 * formats of the supported providers.
 *
 * The conversation is an array of messages, each one of:
 *
 *   { role: "user",      content: "..." }
 *   { role: "assistant", content: "...", tool_calls: [{ id, name, input }] }
 *   { role: "tool",      tool_call_id: "...", name: "...", content: "..." }
 *
 * Anthropic gets the Messages API format; OpenAI and custom endpoints get the
 * (de facto standard) chat completions format.
 */
public class AiChatProtocol
{
   // Tools ------------------------------------------------------------------

   public static final String TOOL_GET_IDE_CONTEXT = "get_ide_context"; //$NON-NLS-1$
   public static final String TOOL_GET_ACTIVE_DOCUMENT = "get_active_document"; //$NON-NLS-1$
   public static final String TOOL_LIST_FILES = "list_files"; //$NON-NLS-1$
   public static final String TOOL_READ_FILE = "read_file"; //$NON-NLS-1$
   public static final String TOOL_WRITE_FILE = "write_file"; //$NON-NLS-1$
   public static final String TOOL_EDIT_FILE = "edit_file"; //$NON-NLS-1$
   public static final String TOOL_RUN_R_CODE = "run_r_code"; //$NON-NLS-1$
   public static final String TOOL_OPEN_FILE = "open_file"; //$NON-NLS-1$
   public static final String TOOL_INSERT_TEXT = "insert_text_in_editor"; //$NON-NLS-1$

   /**
    * True for tools that change files, the R session, or the editor. These
    * need the user's approval unless auto-approve is turned on.
    */
   public static boolean requiresApproval(String toolName)
   {
      return TOOL_WRITE_FILE.equals(toolName) ||
             TOOL_EDIT_FILE.equals(toolName) ||
             TOOL_RUN_R_CODE.equals(toolName) ||
             TOOL_INSERT_TEXT.equals(toolName);
   }

   /** True for tools implemented in the client rather than the R session. */
   public static boolean isClientTool(String toolName)
   {
      return TOOL_GET_ACTIVE_DOCUMENT.equals(toolName) ||
             TOOL_OPEN_FILE.equals(toolName) ||
             TOOL_INSERT_TEXT.equals(toolName);
   }

   private static native JavaScriptObject toolDefinitions() /*-{
      var str = function(description) { return { type: "string", description: description }; };
      var tool = function(name, description, properties, required) {
         return {
            name: name,
            description: description,
            schema: { type: "object", properties: properties, required: required }
         };
      };

      return [
         tool("get_ide_context",
              "Describe the R session: R version, working directory, active project, " +
              "attached packages, and the objects in the global environment.",
              {}, []),
         tool("get_active_document",
              "Get the document the user is currently looking at in the RStudio source " +
              "editor (path, whether it has unsaved changes, the selection, and its full " +
              "contents with line numbers), plus the list of other open documents.",
              {}, []),
         tool("list_files",
              "List files and directories. Directories end with '/'.",
              {
                 path: str("Directory to list, absolute or relative to the working directory. Defaults to '.'."),
                 recursive: { type: "boolean", description: "List the whole tree rather than one level." }
              }, []),
         tool("read_file",
              "Read a text file. The result is prefixed with line numbers, which are not part of the file.",
              {
                 path: str("File path, absolute or relative to the working directory."),
                 start_line: { type: "integer", description: "First line to read (1-based). Optional." },
                 end_line: { type: "integer", description: "Last line to read. Optional." }
              }, ["path"]),
         tool("write_file",
              "Create a file, or replace a file's entire contents. Creates parent directories " +
              "as needed. Prefer edit_file for changes to an existing file.",
              {
                 path: str("File path, absolute or relative to the working directory."),
                 content: str("The complete new contents of the file.")
              }, ["path", "content"]),
         tool("edit_file",
              "Replace one exact, unique piece of text in a file with new text. Read the file " +
              "first; old_text must match the file exactly (without line-number prefixes), " +
              "including whitespace, and must occur exactly once.",
              {
                 path: str("File path, absolute or relative to the working directory."),
                 old_text: str("The exact text to replace."),
                 new_text: str("The replacement text.")
              }, ["path", "old_text", "new_text"]),
         tool("run_r_code",
              "Run R code in the user's R session (in the global environment) and return " +
              "everything it printed, including messages, warnings, and errors. Objects you " +
              "create persist; plots appear in the Plots pane. The rstudioapi package (if " +
              "installed) can be used to drive the IDE.",
              { code: str("The R code to run.") }, ["code"]),
         tool("open_file",
              "Open a file in the RStudio source editor so the user can see it.",
              {
                 path: str("File path, absolute or relative to the working directory."),
                 line: { type: "integer", description: "Line to move the cursor to. Optional." }
              }, ["path"]),
         tool("insert_text_in_editor",
              "Insert text into the active source editor document, replacing the current " +
              "selection (or inserting at the cursor when nothing is selected). Use this for " +
              "untitled or unsaved documents, which edit_file cannot change.",
              { text: str("The text to insert.") }, ["text"])
      ];
   }-*/;

   // Requests ---------------------------------------------------------------

   /**
    * Builds the request body for the given provider. The model name is left
    * for the session to fill in, since it owns the configuration.
    */
   public static native String buildRequestBody(String provider,
                                                String systemPrompt,
                                                JsArray<JavaScriptObject> messages) /*-{
      var tools = @org.rstudio.studio.client.workbench.views.aichat.model.AiChatProtocol::toolDefinitions()();

      // Both APIs reject a tool call that has no result. That happens when
      // the user stops the conversation part way through a set of tool
      // calls, so supply a result for any call that is missing one.
      var answered = {};
      for (var i = 0; i < messages.length; i++)
         if (messages[i].role === "tool")
            answered[messages[i].tool_call_id] = true;

      var repaired = [];
      for (var i = 0; i < messages.length; i++) {
         repaired.push(messages[i]);
         var pending = messages[i].role === "assistant" ? (messages[i].tool_calls || []) : [];
         for (var j = 0; j < pending.length; j++) {
            if (!answered[pending[j].id]) {
               repaired.push({
                  role: "tool",
                  tool_call_id: pending[j].id,
                  name: pending[j].name,
                  content: "The user stopped this tool call before it ran."
               });
            }
         }
      }
      messages = repaired;

      if (provider === "anthropic") {
         var out = [];
         var append = function(role, blocks) {
            var last = out.length ? out[out.length - 1] : null;
            if (last && last.role === role)
               last.content = last.content.concat(blocks);
            else
               out.push({ role: role, content: blocks });
         };

         for (var i = 0; i < messages.length; i++) {
            var m = messages[i];
            if (m.role === "user") {
               append("user", [{ type: "text", text: m.content || "" }]);
            } else if (m.role === "assistant") {
               var blocks = [];
               if (m.content)
                  blocks.push({ type: "text", text: m.content });
               var calls = m.tool_calls || [];
               for (var j = 0; j < calls.length; j++)
                  blocks.push({ type: "tool_use", id: calls[j].id, name: calls[j].name, input: calls[j].input || {} });
               if (blocks.length)
                  append("assistant", blocks);
            } else if (m.role === "tool") {
               append("user", [{ type: "tool_result", tool_use_id: m.tool_call_id, content: m.content || "" }]);
            }
         }

         return JSON.stringify({
            max_tokens: 16000,
            system: systemPrompt,
            tools: tools.map(function(t) {
               return { name: t.name, description: t.description, input_schema: t.schema };
            }),
            messages: out
         });
      }

      // OpenAI chat completions (also used by custom, OpenAI-compatible servers)
      var out = [{ role: "system", content: systemPrompt }];
      for (var i = 0; i < messages.length; i++) {
         var m = messages[i];
         if (m.role === "user") {
            out.push({ role: "user", content: m.content || "" });
         } else if (m.role === "assistant") {
            var message = { role: "assistant", content: m.content || null };
            var calls = m.tool_calls || [];
            if (calls.length) {
               message.tool_calls = calls.map(function(call) {
                  return {
                     id: call.id,
                     type: "function",
                     "function": { name: call.name, arguments: JSON.stringify(call.input || {}) }
                  };
               });
            }
            if (message.content === null && !calls.length)
               message.content = "";
            out.push(message);
         } else if (m.role === "tool") {
            out.push({ role: "tool", tool_call_id: m.tool_call_id, content: m.content || "" });
         }
      }

      return JSON.stringify({
         messages: out,
         tools: tools.map(function(t) {
            return {
               type: "function",
               "function": { name: t.name, description: t.description, parameters: t.schema }
            };
         })
      });
   }-*/;

   // Responses --------------------------------------------------------------

   /**
    * Parses a provider response into an assistant message in the neutral
    * format: { role: "assistant", content, tool_calls }. On failure the
    * returned object has an "error" field instead.
    */
   public static native JavaScriptObject parseResponse(String provider,
                                                       int status,
                                                       String body) /*-{
      var data = null;
      try {
         data = JSON.parse(body);
      } catch (e) {
         var snippet = body ? body.substring(0, 500) : "(empty response)";
         return { error: "HTTP " + status + ": " + snippet };
      }

      var errorMessage = function(data) {
         if (!data || !data.error)
            return null;
         if (typeof data.error === "string")
            return data.error;
         return data.error.message || JSON.stringify(data.error);
      };

      if (status < 200 || status >= 300 || errorMessage(data)) {
         var message = errorMessage(data) || body.substring(0, 500);
         return { error: "HTTP " + status + ": " + message };
      }

      var text = "";
      var calls = [];

      if (provider === "anthropic") {
         var blocks = data.content || [];
         for (var i = 0; i < blocks.length; i++) {
            var block = blocks[i];
            if (block.type === "text")
               text += block.text;
            else if (block.type === "tool_use")
               calls.push({ id: block.id, name: block.name, input: block.input || {} });
         }
         if (data.stop_reason === "max_tokens")
            text += "\n\n_(The response was cut off because it reached the maximum length.)_";
      } else {
         var choice = (data.choices && data.choices[0]) || {};
         var message = choice.message || {};
         if (typeof message.content === "string") {
            text = message.content;
         } else if (message.content && message.content.length) {
            for (var i = 0; i < message.content.length; i++)
               if (message.content[i].text)
                  text += message.content[i].text;
         }
         var toolCalls = message.tool_calls || [];
         for (var i = 0; i < toolCalls.length; i++) {
            var call = toolCalls[i];
            var fn = call["function"] || {};
            var input = {};
            try {
               input = fn.arguments ? JSON.parse(fn.arguments) : {};
            } catch (e) {
               input = { _unparsed_arguments: fn.arguments };
            }
            calls.push({ id: call.id || ("call_" + i + "_" + new Date().getTime()), name: fn.name, input: input });
         }
         if (choice.finish_reason === "length")
            text += "\n\n_(The response was cut off because it reached the maximum length.)_";
      }

      return { role: "assistant", content: text, tool_calls: calls };
   }-*/;

   // Conversation helpers ---------------------------------------------------

   public static native JavaScriptObject userMessage(String text) /*-{
      return { role: "user", content: text };
   }-*/;

   public static native JavaScriptObject toolResultMessage(String toolCallId,
                                                           String name,
                                                           String result) /*-{
      return { role: "tool", tool_call_id: toolCallId, name: name, content: result };
   }-*/;

   public static native String getError(JavaScriptObject reply) /*-{
      return reply.error || null;
   }-*/;

   public static native String getContent(JavaScriptObject message) /*-{
      return message.content || "";
   }-*/;

   public static native JsArray<JavaScriptObject> getToolCalls(JavaScriptObject message) /*-{
      return message.tool_calls || [];
   }-*/;

   public static native String getToolCallId(JavaScriptObject call) /*-{
      return call.id;
   }-*/;

   public static native String getToolCallName(JavaScriptObject call) /*-{
      return call.name || "";
   }-*/;

   public static native JavaScriptObject getToolCallInput(JavaScriptObject call) /*-{
      return call.input || {};
   }-*/;

   /** Reads a string argument from a tool call's input; null when absent. */
   public static native String getStringArg(JavaScriptObject input, String name) /*-{
      var value = input[name];
      if (value === undefined || value === null)
         return null;
      return typeof value === "string" ? value : String(value);
   }-*/;

   /** Reads an integer argument from a tool call's input; -1 when absent. */
   public static native int getIntArg(JavaScriptObject input, String name) /*-{
      var value = parseInt(input[name], 10);
      return isNaN(value) ? -1 : value;
   }-*/;

   /** A pretty-printed form of a tool call's input, for display. */
   public static native String formatInput(JavaScriptObject input) /*-{
      try {
         return JSON.stringify(input, null, 2);
      } catch (e) {
         return String(input);
      }
   }-*/;
}
