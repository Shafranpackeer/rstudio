#
# SessionAiChat.R
#
# Copyright (C) 2026 by Posit Software, PBC
#
# Unless you have received this program directly from Posit Software pursuant
# to the terms of a commercial license agreement with Posit Software, then
# this program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#
#

# Tools the model in the AI pane can call. The client runs the agent loop,
# asks the user before running anything that changes state, and then calls
# 'ai_chat_execute_tool' with the tool name and its (already parsed) input.
# Every tool returns a single string, which is sent back to the model.

# Upper bound on the size of a tool result, so a huge file or a runaway print
# can't blow through the model's context window.
.rs.setVar("aiChat.maxResultChars", 60000L)

.rs.addJsonRpcHandler("ai_chat_execute_tool", function(name, input)
{
   if (!is.list(input))
      input <- list()

   result <- tryCatch(
      .rs.aiChat.executeTool(name, input),
      error = function(e) paste("Error:", conditionMessage(e))
   )

   .rs.scalar(.rs.aiChat.truncate(paste(as.character(result), collapse = "\n")))
})

#' Execute an AI pane tool
#'
#' @param name The name of the tool the model asked for.
#' @param input A named list of the tool's arguments.
.rs.addFunction("aiChat.executeTool", function(name, input)
{
   switch(
      name,
      get_ide_context = .rs.aiChat.getIdeContext(),
      list_files      = .rs.aiChat.listFiles(input$path, input$recursive),
      read_file       = .rs.aiChat.readFile(input$path, input$start_line, input$end_line),
      write_file      = .rs.aiChat.writeFile(input$path, input$content),
      edit_file       = .rs.aiChat.editFile(input$path, input$old_text, input$new_text),
      run_r_code      = .rs.aiChat.runRCode(input$code),
      stop("unknown tool '", name, "'")
   )
})

.rs.addFunction("aiChat.truncate", function(text)
{
   limit <- .rs.getVar("aiChat.maxResultChars")
   if (nchar(text, type = "chars") <= limit)
      return(text)

   paste0(
      substr(text, 1L, limit),
      "\n... [output truncated; ", nchar(text, type = "chars") - limit, " more characters]"
   )
})

.rs.addFunction("aiChat.requireString", function(value, argName)
{
   if (!is.character(value) || length(value) != 1L || is.na(value))
      stop("'", argName, "' must be a single string")
   value
})

.rs.addFunction("aiChat.resolvePath", function(path)
{
   path <- .rs.aiChat.requireString(path, "path")
   path.expand(path)
})

.rs.addFunction("aiChat.getIdeContext", function()
{
   lines <- character()
   add <- function(...) lines <<- c(lines, paste0(...))

   add("R version: ", R.version.string)
   add("Platform: ", R.version$platform)
   add("Working directory: ", getwd())

   project <- tryCatch(.rs.getProjectDirectory(), error = function(e) NULL)
   if (length(project) && nzchar(project))
      add("Active project: ", project)
   else
      add("Active project: (none)")

   add("Attached packages: ", paste(.packages(), collapse = ", "))

   objects <- ls(envir = globalenv())
   if (length(objects))
   {
      add("Objects in the global environment:")
      shown <- utils::head(objects, 100L)
      for (object in shown)
      {
         value <- get(object, envir = globalenv())
         description <- paste(class(value), collapse = "/")
         if (is.data.frame(value))
            description <- sprintf("%s [%d x %d]", description, nrow(value), ncol(value))
         else if (is.atomic(value) || is.list(value))
            description <- sprintf("%s [length %d]", description, length(value))
         add("  ", object, ": ", description)
      }
      if (length(objects) > length(shown))
         add("  ... and ", length(objects) - length(shown), " more")
   }
   else
   {
      add("Objects in the global environment: (none)")
   }

   paste(lines, collapse = "\n")
})

.rs.addFunction("aiChat.listFiles", function(path, recursive)
{
   if (is.null(path) || identical(path, ""))
      path <- "."
   path <- .rs.aiChat.resolvePath(path)
   recursive <- isTRUE(recursive)

   if (!dir.exists(path))
      stop("directory '", path, "' does not exist")

   files <- list.files(
      path,
      recursive    = recursive,
      all.files    = FALSE,
      include.dirs = TRUE,
      no..         = TRUE
   )

   # skip noise that is never useful to the model
   files <- files[!grepl("(^|/)(\\.git|\\.Rproj\\.user|node_modules|renv/library)(/|$)", files)]

   if (!length(files))
      return(paste0("(", path, " is empty)"))

   limit <- 1000L
   isDir <- dir.exists(file.path(path, files))
   files[isDir] <- paste0(files[isDir], "/")
   result <- utils::head(files, limit)
   if (length(files) > limit)
      result <- c(result, sprintf("... and %d more", length(files) - limit))

   paste(result, collapse = "\n")
})

.rs.addFunction("aiChat.readFile", function(path, startLine, endLine)
{
   path <- .rs.aiChat.resolvePath(path)
   if (!file.exists(path) || dir.exists(path))
      stop("file '", path, "' does not exist")

   contents <- readLines(path, warn = FALSE, encoding = "UTF-8")
   total <- length(contents)

   first <- if (is.numeric(startLine)) max(1L, as.integer(startLine)) else 1L
   last <- if (is.numeric(endLine)) min(total, as.integer(endLine)) else total
   if (total == 0L)
      return(paste0("(", path, " is empty)"))
   if (first > last)
      stop("requested line range is empty (file has ", total, " lines)")

   selected <- contents[first:last]
   numbered <- sprintf("%*d  %s", nchar(as.character(last)), first:last, selected)
   paste(c(sprintf("%s (lines %d-%d of %d)", path, first, last, total), numbered), collapse = "\n")
})

.rs.addFunction("aiChat.writeContents", function(path, contents)
{
   dir <- dirname(path)
   if (!dir.exists(dir))
      dir.create(dir, recursive = TRUE, showWarnings = FALSE)

   con <- file(path, open = "wb")
   on.exit(close(con), add = TRUE)
   writeBin(charToRaw(enc2utf8(contents)), con)
})

.rs.addFunction("aiChat.writeFile", function(path, content)
{
   path <- .rs.aiChat.resolvePath(path)
   content <- .rs.aiChat.requireString(content, "content")
   existed <- file.exists(path)
   .rs.aiChat.writeContents(path, content)
   sprintf("%s %s (%d characters).",
           if (existed) "Overwrote" else "Created",
           path,
           nchar(content, type = "chars"))
})

.rs.addFunction("aiChat.editFile", function(path, oldText, newText)
{
   path <- .rs.aiChat.resolvePath(path)
   oldText <- .rs.aiChat.requireString(oldText, "old_text")
   newText <- .rs.aiChat.requireString(newText, "new_text")

   if (!file.exists(path) || dir.exists(path))
      stop("file '", path, "' does not exist")
   if (!nzchar(oldText))
      stop("'old_text' must not be empty")

   size <- file.info(path)$size
   contents <- if (size > 0) readChar(path, size, useBytes = TRUE) else ""
   Encoding(contents) <- "UTF-8"

   matches <- gregexpr(oldText, contents, fixed = TRUE)[[1L]]
   count <- if (identical(as.integer(matches[[1L]]), -1L)) 0L else length(matches)
   if (count == 0L)
      stop("'old_text' was not found in ", path, "; read the file again and retry with the exact text")
   if (count > 1L)
      stop("'old_text' matches ", count, " places in ", path, "; include more surrounding text so it is unique")

   start <- matches[[1L]]
   end <- start + attr(matches, "match.length")[[1L]]
   updated <- paste0(
      substr(contents, 1L, start - 1L),
      newText,
      substr(contents, end, nchar(contents, type = "chars"))
   )

   .rs.aiChat.writeContents(path, updated)
   paste("Edited", path)
})

.rs.addFunction("aiChat.runRCode", function(code)
{
   code <- .rs.aiChat.requireString(code, "code")
   exprs <- parse(text = code, keep.source = FALSE)

   output <- character()
   con <- textConnection("output", open = "w", local = TRUE)
   sink(con)
   sink(con, type = "message")
   on.exit({
      sink(type = "message")
      sink()
      close(con)
   }, add = TRUE)

   failed <- FALSE
   withCallingHandlers(
      tryCatch({
         for (expr in exprs)
         {
            result <- withVisible(eval(expr, envir = globalenv()))
            if (result$visible)
               print(result$value)
         }
      }, error = function(e) {
         failed <<- TRUE
         cat("Error: ", conditionMessage(e), "\n", sep = "", file = stderr())
      }),
      warning = function(w) {
         cat("Warning: ", conditionMessage(w), "\n", sep = "", file = stderr())
         invokeRestart("muffleWarning")
      }
   )

   # flush the connection so the final line is captured
   sink(type = "message")
   sink()
   close(con)
   on.exit()

   text <- paste(output, collapse = "\n")
   if (!nzchar(text))
      text <- if (failed) "(the code failed without output)" else "(the code ran successfully with no output)"
   text
})
