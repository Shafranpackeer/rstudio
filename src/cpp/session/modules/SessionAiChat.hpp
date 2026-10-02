/*
 * SessionAiChat.hpp
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

#ifndef SESSION_AI_CHAT_HPP
#define SESSION_AI_CHAT_HPP

namespace rstudio {
namespace core {
class Error;
}
}

namespace rstudio {
namespace session {
namespace modules {
namespace ai_chat {

// The "AI" pane: a bring-your-own-model chat that talks to Anthropic,
// OpenAI, or any OpenAI-compatible endpoint. This module owns the provider
// configuration (including the API key, which is never sent to the client)
// and proxies model requests; the agent loop itself runs in the client, and
// the tools the model may call are implemented in SessionAiChat.R.
core::Error initialize();

} // namespace ai_chat
} // namespace modules
} // namespace session
} // namespace rstudio

#endif // SESSION_AI_CHAT_HPP
