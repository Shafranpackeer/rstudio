/*
 * AiChatPane.java
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

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.resources.ImageResource2x;
import org.rstudio.core.client.widget.ThemedButton;
import org.rstudio.core.client.widget.Toolbar;
import org.rstudio.core.client.widget.ToolbarButton;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.icons.StandardIcons;
import org.rstudio.studio.client.workbench.ui.WorkbenchPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class AiChatPane extends WorkbenchPane implements AiChatPresenter.Display
{
   @Inject
   public AiChatPane(EventBus events)
   {
      super(constants_.aiChatTitle(), events);
      RES.styles().ensureInjected();
      ensureWidget();
   }

   // WorkbenchPane ----------------------------------------------------------

   @Override
   protected Toolbar createMainToolbar()
   {
      Toolbar toolbar = new Toolbar(constants_.aiChatTabLabel());

      status_ = new Label();
      status_.addStyleName(RES.styles().status());
      toolbar.addLeftWidget(status_);

      ToolbarButton newChat = new ToolbarButton(
            ToolbarButton.NoText,
            constants_.newChat(),
            new ImageResource2x(StandardIcons.INSTANCE.stock_new2x()),
            event -> { if (observer_ != null) observer_.onNewChat(); });
      toolbar.addRightWidget(newChat);

      ToolbarButton settings = new ToolbarButton(
            ToolbarButton.NoText,
            constants_.settings(),
            new ImageResource2x(StandardIcons.INSTANCE.options2x()),
            event -> { if (observer_ != null) observer_.onShowSettings(); });
      toolbar.addRightWidget(settings);

      return toolbar;
   }

   @Override
   protected Widget createMainWidget()
   {
      transcript_ = new FlowPanel();
      transcript_.addStyleName(RES.styles().transcript());

      scroller_ = new ScrollPanel(transcript_);

      input_ = new TextArea();
      input_.addStyleName(RES.styles().input());
      input_.getElement().setAttribute("placeholder", constants_.inputPlaceholder());
      input_.getElement().setAttribute("aria-label", constants_.inputLabel());
      input_.addKeyDownHandler(event ->
      {
         if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE && busy_)
         {
            event.preventDefault();
            if (observer_ != null)
               observer_.onStop();
            return;
         }

         if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER &&
             !event.getNativeEvent().getShiftKey() &&
             !event.getNativeEvent().getAltKey() &&
             !event.getNativeEvent().getCtrlKey() &&
             !event.getNativeEvent().getMetaKey())
         {
            event.preventDefault();
            submit();
         }
      });

      autoApprove_ = new CheckBox(constants_.autoApprove());
      autoApprove_.setTitle(constants_.autoApproveTitle());
      autoApprove_.addStyleName(RES.styles().autoApprove());

      sendButton_ = new ThemedButton(constants_.send(), event ->
      {
         if (busy_)
         {
            if (observer_ != null)
               observer_.onStop();
         }
         else
         {
            submit();
         }
      });

      FlowPanel buttons = new FlowPanel();
      buttons.addStyleName(RES.styles().inputButtons());
      buttons.add(autoApprove_);
      buttons.add(sendButton_);

      FlowPanel inputArea = new FlowPanel();
      inputArea.addStyleName(RES.styles().inputArea());
      inputArea.add(input_);
      inputArea.add(buttons);

      DockLayoutPanel panel = new DockLayoutPanel(Unit.PX);
      panel.addStyleName(RES.styles().root());
      panel.addSouth(inputArea, 116);
      panel.add(scroller_);
      return panel;
   }

   @Override
   public void setFocus()
   {
      focusInput();
   }

   // Display ----------------------------------------------------------------

   @Override
   public void setObserver(AiChatPresenter.Display.Observer observer)
   {
      observer_ = observer;
   }

   @Override
   public void setStatus(String status)
   {
      status_.setText(status);
      status_.setTitle(status);
   }

   @Override
   public void setBusy(boolean busy)
   {
      busy_ = busy;
      sendButton_.setText(busy ? constants_.stop() : constants_.send());
      if (busy)
      {
         if (thinking_ == null)
         {
            thinking_ = new Label(constants_.thinking());
            thinking_.addStyleName(RES.styles().thinking());
         }
         transcript_.add(thinking_);
         scrollToBottom();
      }
      else if (thinking_ != null)
      {
         thinking_.removeFromParent();
      }
   }

   @Override
   public void clearTranscript()
   {
      transcript_.clear();
      welcome_ = null;
   }

   @Override
   public void showWelcome(boolean configured)
   {
      clearTranscript();

      welcome_ = new FlowPanel();
      welcome_.addStyleName(RES.styles().welcome());

      Label title = new Label(constants_.welcomeTitle());
      title.addStyleName(RES.styles().welcomeTitle());
      welcome_.add(title);

      Label text = new Label(constants_.welcomeMessage());
      text.addStyleName(RES.styles().welcomeText());
      welcome_.add(text);

      Label toolsNote = new Label(constants_.welcomeToolsNote());
      toolsNote.addStyleName(RES.styles().welcomeText());
      toolsNote.getElement().getStyle().setFontSize(11, Unit.PX);
      welcome_.add(toolsNote);

      if (!configured)
      {
         welcome_.add(new ThemedButton(constants_.configureButton(), event ->
         {
            if (observer_ != null)
               observer_.onShowSettings();
         }));
      }

      transcript_.add(welcome_);
   }

   @Override
   public void addUserMessage(String text)
   {
      removeWelcome();

      FlowPanel message = new FlowPanel();
      message.addStyleName(RES.styles().message());

      Label speaker = new Label(constants_.userLabel());
      speaker.addStyleName(RES.styles().speaker());
      message.add(speaker);

      Label body = new Label(text);
      body.addStyleName(RES.styles().userMessage());
      message.add(body);

      append(message);
   }

   @Override
   public void addAssistantMessage(String markdown)
   {
      removeWelcome();

      FlowPanel message = new FlowPanel();
      message.addStyleName(RES.styles().message());
      message.addStyleName(RES.styles().assistantMessage());
      renderMarkdown(markdown, message);
      append(message);
   }

   @Override
   public void addErrorMessage(String text)
   {
      Label label = new Label(constants_.error() + ": " + text);
      label.addStyleName(RES.styles().message());
      label.addStyleName(RES.styles().errorMessage());
      append(label);
   }

   @Override
   public void addInfoMessage(String text)
   {
      Label label = new Label(text);
      label.addStyleName(RES.styles().message());
      label.addStyleName(RES.styles().infoMessage());
      append(label);
   }

   @Override
   public AiChatPresenter.Display.ToolCallView addToolCall(String name,
                                                           String summary,
                                                           String input)
   {
      removeWelcome();
      ToolCallWidget widget = new ToolCallWidget(name, summary, input);
      append(widget);
      return widget;
   }

   @Override
   public boolean isAutoApprove()
   {
      return autoApprove_.getValue();
   }

   @Override
   public void focusInput()
   {
      Scheduler.get().scheduleDeferred(() -> input_.setFocus(true));
   }

   // Implementation ---------------------------------------------------------

   private void submit()
   {
      String text = input_.getText().trim();
      if (text.isEmpty() || busy_ || observer_ == null)
         return;

      input_.setText("");
      observer_.onSend(text);
   }

   private void removeWelcome()
   {
      if (welcome_ != null)
      {
         welcome_.removeFromParent();
         welcome_ = null;
      }
   }

   private void append(Widget widget)
   {
      // keep the "thinking" indicator at the bottom of the transcript
      if (thinking_ != null && thinking_.getParent() == transcript_)
         transcript_.insert(widget, transcript_.getWidgetIndex(thinking_));
      else
         transcript_.add(widget);
      scrollToBottom();
   }

   private void scrollToBottom()
   {
      Scheduler.get().scheduleDeferred(() -> scroller_.scrollToBottom());
   }

   /**
    * Renders the small subset of Markdown models commonly produce: fenced
    * code blocks (with copy / insert buttons), inline code, bold text, and
    * headings. Everything is HTML-escaped before any markup is added.
    */
   private void renderMarkdown(String markdown, FlowPanel container)
   {
      String[] lines = StringUtil.notNull(markdown).split("\n", -1);
      StringBuilder text = new StringBuilder();
      StringBuilder code = null;
      String language = "";

      for (String line : lines)
      {
         if (line.trim().startsWith("```"))
         {
            if (code == null)
            {
               flushText(text, container);
               code = new StringBuilder();
               language = line.trim().substring(3).trim();
            }
            else
            {
               container.add(createCodeBlock(code.toString(), language));
               code = null;
            }
            continue;
         }

         if (code != null)
         {
            if (code.length() > 0)
               code.append("\n");
            code.append(line);
         }
         else
         {
            text.append(line).append("\n");
         }
      }

      // an unterminated fence still renders as code
      if (code != null)
         container.add(createCodeBlock(code.toString(), language));
      flushText(text, container);
   }

   private void flushText(StringBuilder text, FlowPanel container)
   {
      String value = text.toString().trim();
      text.setLength(0);
      if (value.isEmpty())
         return;

      StringBuilder html = new StringBuilder();
      for (String paragraph : value.split("\n\\s*\n"))
      {
         StringBuilder lines = new StringBuilder();
         for (String line : paragraph.split("\n"))
         {
            if (lines.length() > 0)
               lines.append("<br/>");
            lines.append(formatInline(line));
         }
         html.append("<p>").append(lines).append("</p>");
      }

      container.add(new HTML(html.toString()));
   }

   private String formatInline(String line)
   {
      String escaped = SafeHtmlUtils.htmlEscape(line);

      // headings render as bold lines
      if (escaped.matches("^#{1,6} .*"))
         return "<strong>" + escaped.replaceFirst("^#{1,6} ", "") + "</strong>";

      return escaped
            .replaceAll("`([^`]+)`", "<code>$1</code>")
            .replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
   }

   private Widget createCodeBlock(String code, String language)
   {
      FlowPanel block = new FlowPanel();
      block.addStyleName(RES.styles().codeBlock());

      FlowPanel header = new FlowPanel();
      header.addStyleName(RES.styles().codeHeader());

      Label languageLabel = new Label(language);
      languageLabel.addStyleName(RES.styles().codeLanguage());
      header.add(languageLabel);

      Button copy = new Button(constants_.copy(), (ClickHandler) event ->
            copyToClipboard(code));
      header.add(copy);

      Button insert = new Button(constants_.insert(), (ClickHandler) event ->
      {
         if (observer_ != null)
            observer_.onInsertCode(code);
      });
      insert.setTitle(constants_.insertTitle());
      header.add(insert);

      block.add(header);
      block.add(new HTML("<pre>" + SafeHtmlUtils.htmlEscape(code) + "</pre>"));
      return block;
   }

   private static native void copyToClipboard(String text) /*-{
      if ($wnd.navigator.clipboard)
         $wnd.navigator.clipboard.writeText(text);
   }-*/;

   private class ToolCallWidget extends FlowPanel
         implements AiChatPresenter.Display.ToolCallView
   {
      ToolCallWidget(String name, String summary, String input)
      {
         addStyleName(RES.styles().toolCall());

         details_ = new FlowPanel();
         details_.addStyleName(RES.styles().toolDetails());
         details_.add(new HTML("<pre>" + SafeHtmlUtils.htmlEscape(input) + "</pre>"));
         details_.setVisible(false);

         FlowPanel header = new FlowPanel();
         header.addStyleName(RES.styles().toolHeader());

         Label nameLabel = new Label(name);
         nameLabel.addStyleName(RES.styles().toolName());
         header.add(nameLabel);

         Label summaryLabel = new Label(summary);
         summaryLabel.addStyleName(RES.styles().toolSummary());
         summaryLabel.setTitle(summary);
         header.add(summaryLabel);

         toolStatus_ = new Label(constants_.running());
         toolStatus_.addStyleName(RES.styles().toolStatus());
         header.add(toolStatus_);

         header.addDomHandler(event -> details_.setVisible(!details_.isVisible()),
               ClickEvent.getType());
         header.setTitle(constants_.showDetails());
         add(header);

         add(details_);
      }

      @Override
      public void requestApproval(Command onApprove, Command onDeny)
      {
         toolStatus_.setText(constants_.waitingForApproval());
         details_.setVisible(true);

         approval_ = new FlowPanel();
         approval_.addStyleName(RES.styles().toolApproval());
         approval_.add(new ThemedButton(constants_.approve(), event ->
         {
            removeApproval();
            onApprove.execute();
         }));
         approval_.add(new ThemedButton(constants_.deny(), event ->
         {
            removeApproval();
            onDeny.execute();
         }));
         add(approval_);
         scrollToBottom();
      }

      @Override
      public void setRunning()
      {
         removeApproval();
         toolStatus_.setText(constants_.running());
      }

      @Override
      public void setResult(String result, boolean success)
      {
         removeApproval();
         toolStatus_.setText(success ? constants_.done() : constants_.failed());

         Label label = new Label(constants_.showDetails());
         label.setText("→"); //$NON-NLS-1$
         label.addStyleName(RES.styles().toolDetailsLabel());
         details_.add(label);
         details_.add(new HTML("<pre>" + SafeHtmlUtils.htmlEscape(result) + "</pre>"));

         // collapse the details once an approved action has finished
         details_.setVisible(false);
      }

      @Override
      public void setDenied()
      {
         removeApproval();
         toolStatus_.setText(constants_.denied());
         details_.setVisible(false);
      }

      private void removeApproval()
      {
         if (approval_ != null)
         {
            approval_.removeFromParent();
            approval_ = null;
         }
      }

      private final Label toolStatus_;
      private final FlowPanel details_;
      private FlowPanel approval_;
   }

   // Resources --------------------------------------------------------------

   public interface Styles extends CssResource
   {
      String root();
      String transcript();
      String welcome();
      String welcomeTitle();
      String welcomeText();
      String message();
      String userMessage();
      String speaker();
      String assistantMessage();
      String codeBlock();
      String codeHeader();
      String codeLanguage();
      String errorMessage();
      String infoMessage();
      String toolCall();
      String toolHeader();
      String toolName();
      String toolSummary();
      String toolStatus();
      String toolDetails();
      String toolDetailsLabel();
      String toolApproval();
      String thinking();
      String inputArea();
      String input();
      String inputButtons();
      String autoApprove();
      String status();
   }

   public interface Resources extends ClientBundle
   {
      @Source("AiChatPane.css")
      Styles styles();
   }

   private static final Resources RES = GWT.create(Resources.class);
   private static final AiChatConstants constants_ = GWT.create(AiChatConstants.class);

   private AiChatPresenter.Display.Observer observer_;
   private Label status_;
   private FlowPanel transcript_;
   private ScrollPanel scroller_;
   private TextArea input_;
   private CheckBox autoApprove_;
   private ThemedButton sendButton_;
   private Label thinking_;
   private FlowPanel welcome_;
   private boolean busy_;
}
