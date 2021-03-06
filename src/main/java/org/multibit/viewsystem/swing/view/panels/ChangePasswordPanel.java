/**
 * Copyright 2012 multibit.org
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multibit.viewsystem.swing.view.panels;

import org.bitcoinj.wallet.Protos.Wallet.EncryptionType;
import org.multibit.controller.Controller;
import org.multibit.controller.bitcoin.BitcoinController;
import org.multibit.model.bitcoin.WalletBusyListener;
import org.multibit.utils.ImageLoader;
import org.multibit.viewsystem.DisplayHint;
import org.multibit.viewsystem.View;
import org.multibit.viewsystem.Viewable;
import org.multibit.viewsystem.swing.ColorAndFontConstants;
import org.multibit.viewsystem.swing.MultiBitFrame;
import org.multibit.viewsystem.swing.action.ChangePasswordSubmitAction;
import org.multibit.viewsystem.swing.action.HelpContextAction;
import org.multibit.viewsystem.swing.view.components.HelpButton;
import org.multibit.viewsystem.swing.view.components.MultiBitButton;
import org.multibit.viewsystem.swing.view.components.MultiBitLabel;
import org.multibit.viewsystem.swing.view.components.MultiBitTitledPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;


/**
 * The change password view.
 */
public class ChangePasswordPanel extends JPanel implements Viewable, WalletBusyListener {

  private static final long serialVersionUID = 444992298432957705L;

  private final Controller controller;
  private final BitcoinController bitcoinController;

  private MultiBitFrame mainFrame;

  private MultiBitLabel walletFilenameLabel;

  private MultiBitLabel walletDescriptionLabel;

  private MultiBitLabel messageLabel1;
  private MultiBitLabel messageLabel2;
  private MultiBitLabel messageLabel3;

  private MultiBitLabel reminderLabel1;
  private MultiBitLabel reminderLabel2;
  private MultiBitLabel reminderLabel3;

  private JPasswordField currentPasswordField;

  private JPasswordField newPasswordField;
  private JPasswordField repeatNewPasswordField;

  private ChangePasswordSubmitAction changePasswordSubmitAction;

  private JLabel tickLabel;

  public static final int STENT_HEIGHT = 12;
  public static final int STENT_DELTA = 20;

  /**
   * Creates a new {@link ChangePasswordPanel}.
   */
  public ChangePasswordPanel(BitcoinController bitcoinController, MultiBitFrame mainFrame) {
    this.bitcoinController = bitcoinController;
    this.controller = this.bitcoinController;

    this.mainFrame = mainFrame;

    setBackground(ColorAndFontConstants.VERY_LIGHT_BACKGROUND_COLOR);
    applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    initUI();

    walletBusyChange(this.bitcoinController.getModel().getActivePerWalletModelData().isBusy());
    this.bitcoinController.registerWalletBusyListener(this);
  }

  @Override
  public void navigateAwayFromView() {
  }

  private void initUI() {
    setLayout(new BorderLayout());

    JPanel mainPanel = new JPanel();
    mainPanel.setMinimumSize(new Dimension(550, 160));
    mainPanel.setLayout(new GridBagLayout());
    mainPanel.setOpaque(false);
    mainPanel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    String[] keys = new String[]{"resetTransactionsPanel.walletDescriptionLabel",
            "resetTransactionsPanel.walletFilenameLabel", "showExportPrivateKeysPanel.passwordPrompt",
            "showExportPrivateKeysPanel.repeatPasswordPrompt", "showImportPrivateKeysPanel.numberOfKeys.text",
            "showImportPrivateKeysPanel.replayDate.text"};

    int stentWidth = MultiBitTitledPanel.calculateStentWidthForKeys(controller.getLocaliser(), keys, this) + STENT_DELTA;

    GridBagConstraints constraints = new GridBagConstraints();

    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.gridwidth = 2;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(createWalletPanel(stentWidth), constraints);

    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 2;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(createCurrentPasswordPanel(stentWidth), constraints);

    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.gridwidth = 2;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(createNewPasswordPanel(stentWidth), constraints);

    JLabel filler1 = new JLabel();
    filler1.setOpaque(false);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 0;
    constraints.gridy = 3;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.weightx = 1;
    constraints.weighty = 0.1;
    constraints.anchor = GridBagConstraints.CENTER;
    mainPanel.add(filler1, constraints);

    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 0;
    constraints.gridy = 4;
    constraints.gridwidth = 1;
    constraints.weightx = 0.4;
    constraints.weighty = 0.06;
    constraints.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(createButtonPanel(), constraints);

    messageLabel1 = new MultiBitLabel("");
    messageLabel1.setOpaque(false);
    messageLabel1.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
    messageLabel1.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 0;
    constraints.gridy = 5;
    constraints.gridwidth = 1;
    constraints.weightx = 1;
    constraints.weighty = 0.06;
    constraints.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(messageLabel1, constraints);

    messageLabel2 = new MultiBitLabel("");
    messageLabel2.setOpaque(false);
    messageLabel2.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
    messageLabel2.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 0;
    constraints.gridy = 6;
    constraints.gridwidth = 1;
    constraints.weightx = 1;
    constraints.weighty = 0.06;
    constraints.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(messageLabel2, constraints);

    messageLabel3 = new MultiBitLabel("");
    messageLabel3.setOpaque(false);
    messageLabel3.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 30));
    messageLabel3.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 0;
    constraints.gridy = 7;
    constraints.gridwidth = 1;
    constraints.weightx = 1;
    constraints.weighty = 0.06;
    constraints.anchor = GridBagConstraints.LINE_START;
    mainPanel.add(messageLabel3, constraints);

    Action helpAction = new HelpContextAction(controller, ImageLoader.HELP_CONTENTS_BIG_ICON_FILE,
            "multiBitFrame.helpMenuText", "multiBitFrame.helpMenuTooltip", "multiBitFrame.helpMenuText",
            HelpContentsPanel.HELP_PASSWORD_PROTECTION_URL);
    HelpButton helpButton = new HelpButton(helpAction, controller);
    helpButton.setText("");
    helpButton.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    String tooltipText = HelpContentsPanel.createMultilineTooltipText(new String[]{
            controller.getLocaliser().getString("multiBitFrame.helpMenuTooltip")});
    helpButton.setToolTipText(tooltipText);
    helpButton.setHorizontalAlignment(SwingConstants.LEADING);
    helpButton.setBorder(BorderFactory.createEmptyBorder(0, AbstractTradePanel.HELP_BUTTON_INDENT, AbstractTradePanel.HELP_BUTTON_INDENT, AbstractTradePanel.HELP_BUTTON_INDENT));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 0;
    constraints.gridy = 8;
    constraints.weightx = 1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.BASELINE_LEADING;
    mainPanel.add(helpButton, constraints);

    JLabel filler2 = new JLabel();
    filler2.setOpaque(false);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 0;
    constraints.gridy = 9;
    constraints.gridwidth = 1;
    constraints.weightx = 1;
    constraints.weighty = 100;
    constraints.anchor = GridBagConstraints.CENTER;
    mainPanel.add(filler2, constraints);

    JScrollPane mainScrollPane = new JScrollPane(mainPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    mainScrollPane.setBorder(BorderFactory.createEmptyBorder());
    mainScrollPane.getViewport().setBackground(ColorAndFontConstants.VERY_LIGHT_BACKGROUND_COLOR);
    mainScrollPane.getViewport().setOpaque(true);
    mainScrollPane.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    add(mainScrollPane, BorderLayout.CENTER);
  }

  private JPanel createWalletPanel(int stentWidth) {
    MultiBitTitledPanel inputWalletPanel = new MultiBitTitledPanel(controller.getLocaliser().getString(
            "showExportPrivateKeysPanel.wallet.title"), ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    GridBagConstraints constraints = new GridBagConstraints();

    MultiBitTitledPanel.addLeftJustifiedTextAtIndent(
            controller.getLocaliser().getString("changePasswordPanel.text"), 3, inputWalletPanel);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 4;
    constraints.weightx = 0.3;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    inputWalletPanel.add(MultiBitTitledPanel.createStent(stentWidth, STENT_HEIGHT), constraints);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 2;
    constraints.gridy = 5;
    constraints.weightx = 0.05;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    inputWalletPanel.add(MultiBitTitledPanel.createStent(MultiBitTitledPanel.SEPARATION_BETWEEN_NAME_VALUE_PAIRS), constraints);

    JPanel filler0 = new JPanel();
    filler0.setOpaque(false);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 3;
    constraints.gridy = 4;
    constraints.weightx = 100;
    constraints.weighty = 1;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    inputWalletPanel.add(filler0, constraints);

    MultiBitLabel walletDescriptionLabelLabel = new MultiBitLabel(controller.getLocaliser().getString(
            "resetTransactionsPanel.walletDescriptionLabel"));
    walletDescriptionLabelLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 1;
    constraints.gridy = 5;
    constraints.weightx = 0.5;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    inputWalletPanel.add(walletDescriptionLabelLabel, constraints);

    walletDescriptionLabel = new MultiBitLabel(this.bitcoinController.getModel().getActivePerWalletModelData().getWalletDescription());
    walletDescriptionLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 5;
    constraints.weightx = 0.5;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    inputWalletPanel.add(walletDescriptionLabel, constraints);

    MultiBitLabel walletFilenameLabelLabel = new MultiBitLabel(controller.getLocaliser().getString(
            "resetTransactionsPanel.walletFilenameLabel"));
    walletFilenameLabelLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 1;
    constraints.gridy = 6;
    constraints.weightx = 0.5;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    inputWalletPanel.add(walletFilenameLabelLabel, constraints);

    walletFilenameLabel = new MultiBitLabel(this.bitcoinController.getModel().getActiveWalletFilename());
    walletFilenameLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 6;
    constraints.weightx = 0.5;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    inputWalletPanel.add(walletFilenameLabel, constraints);

    JPanel fill1 = new JPanel();
    fill1.setOpaque(false);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 3;
    constraints.gridy = 7;
    constraints.weightx = 20;
    constraints.weighty = 1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    inputWalletPanel.add(fill1, constraints);

    JPanel filler3 = new JPanel();
    filler3.setOpaque(false);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 7;
    constraints.weightx = 0.3;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    inputWalletPanel.add(filler3, constraints);

    return inputWalletPanel;
  }

  private JPanel createCurrentPasswordPanel(int stentWidth) {
    MultiBitTitledPanel currentPasswordPanel = new MultiBitTitledPanel(controller.getLocaliser().getString(
            "changePasswordPanel.currentPassword.title"), ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    GridBagConstraints constraints = new GridBagConstraints();

    MultiBitTitledPanel.addLeftJustifiedTextAtIndent(
            controller.getLocaliser().getString("removePasswordPanel.enterPassword.text"), 3, currentPasswordPanel);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 0;
    constraints.gridy = 3;
    constraints.weightx = 0.1;
    constraints.weighty = 0.05;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    JPanel indent = MultiBitTitledPanel.getIndentPanel(1);
    currentPasswordPanel.add(indent, constraints);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 3;
    constraints.weightx = 0.3;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    JPanel stent = MultiBitTitledPanel.createStent(stentWidth, STENT_HEIGHT);
    currentPasswordPanel.add(stent, constraints);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 2;
    constraints.gridy = 3;
    constraints.weightx = 0.05;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    currentPasswordPanel.add(MultiBitTitledPanel.createStent(MultiBitTitledPanel.SEPARATION_BETWEEN_NAME_VALUE_PAIRS), constraints);

    JPanel filler0 = new JPanel();
    filler0.setOpaque(false);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 4;
    constraints.gridy = 3;
    constraints.weightx = 100;
    constraints.weighty = 1;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    currentPasswordPanel.add(filler0, constraints);

    MultiBitLabel passwordPromptLabel = new MultiBitLabel("");
    passwordPromptLabel.setText(controller.getLocaliser().getString("showExportPrivateKeysPanel.passwordPrompt"));
    passwordPromptLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 1;
    constraints.gridy = 4;
    constraints.weightx = 0.3;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    currentPasswordPanel.add(passwordPromptLabel, constraints);

    currentPasswordField = new JPasswordField(24);
    currentPasswordField.setMinimumSize(new Dimension(200, 20));
    currentPasswordField.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 4;
    constraints.weightx = 0.3;
    constraints.weighty = 0.25;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    currentPasswordPanel.add(currentPasswordField, constraints);

    JLabel filler3 = new JLabel();
    filler3.setMinimumSize(new Dimension(3, 3));
    filler3.setMaximumSize(new Dimension(3, 3));
    filler3.setPreferredSize(new Dimension(3, 3));
    filler3.setOpaque(false);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 5;
    constraints.weightx = 0.1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    currentPasswordPanel.add(filler3, constraints);

    JLabel filler4 = new JLabel();
    filler4.setMinimumSize(new Dimension(3, 12));
    filler4.setMaximumSize(new Dimension(3, 12));
    filler4.setPreferredSize(new Dimension(3, 12));
    filler4.setOpaque(false);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 7;
    constraints.weightx = 0.1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    currentPasswordPanel.add(filler4, constraints);

    JLabel filler5 = new JLabel();
    filler5.setMinimumSize(new Dimension(3, 8));
    filler5.setMaximumSize(new Dimension(3, 8));
    filler5.setPreferredSize(new Dimension(3, 8));
    filler5.setOpaque(false);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 8;
    constraints.weightx = 0.1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    currentPasswordPanel.add(filler5, constraints);

    return currentPasswordPanel;
  }

  private JPanel createNewPasswordPanel(int stentWidth) {
    MultiBitTitledPanel passwordProtectPanel = new MultiBitTitledPanel(controller.getLocaliser().getString(
            "changePasswordPanel.newPassword.title"), ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    GridBagConstraints constraints = new GridBagConstraints();

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 0;
    constraints.gridy = 3;
    constraints.weightx = 0.1;
    constraints.weighty = 0.05;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    JPanel indent = MultiBitTitledPanel.getIndentPanel(1);
    passwordProtectPanel.add(indent, constraints);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 3;
    constraints.weightx = 0.3;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    JPanel stent = MultiBitTitledPanel.createStent(stentWidth, STENT_HEIGHT);
    passwordProtectPanel.add(stent, constraints);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 2;
    constraints.gridy = 3;
    constraints.weightx = 0.05;
    constraints.weighty = 0.3;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    passwordProtectPanel.add(MultiBitTitledPanel.createStent(MultiBitTitledPanel.SEPARATION_BETWEEN_NAME_VALUE_PAIRS), constraints);

    JPanel filler0 = new JPanel();
    filler0.setOpaque(false);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 4;
    constraints.gridy = 3;
    constraints.weightx = 100;
    constraints.weighty = 1;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    passwordProtectPanel.add(filler0, constraints);

    MultiBitLabel passwordPromptLabel = new MultiBitLabel("");
    passwordPromptLabel.setText(controller.getLocaliser().getString("changePasswordPanel.newPasswordPrompt"));
    passwordPromptLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 1;
    constraints.gridy = 4;
    constraints.weightx = 0.3;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    passwordProtectPanel.add(passwordPromptLabel, constraints);

    newPasswordField = new JPasswordField(24);
    newPasswordField.setMinimumSize(new Dimension(200, 20));
    newPasswordField.addKeyListener(new PasswordListener());
    newPasswordField.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 4;
    constraints.weightx = 0.3;
    constraints.weighty = 0.25;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    passwordProtectPanel.add(newPasswordField, constraints);

    JLabel filler3 = new JLabel();
    filler3.setMinimumSize(new Dimension(3, 3));
    filler3.setMaximumSize(new Dimension(3, 3));
    filler3.setPreferredSize(new Dimension(3, 3));
    filler3.setOpaque(false);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 5;
    constraints.weightx = 0.1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    passwordProtectPanel.add(filler3, constraints);

    MultiBitLabel repeatPasswordPromptLabel = new MultiBitLabel("");
    repeatPasswordPromptLabel.setText(controller.getLocaliser().getString("changePasswordPanel.repeatNewPasswordPrompt"));
    repeatPasswordPromptLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 1;
    constraints.gridy = 6;
    constraints.weightx = 0.3;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_END;
    passwordProtectPanel.add(repeatPasswordPromptLabel, constraints);

    repeatNewPasswordField = new JPasswordField(24);
    repeatNewPasswordField.setMinimumSize(new Dimension(200, 20));
    repeatNewPasswordField.addKeyListener(new PasswordListener());
    repeatNewPasswordField.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 6;
    constraints.weightx = 0.3;
    constraints.weighty = 0.25;
    constraints.gridwidth = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    passwordProtectPanel.add(repeatNewPasswordField, constraints);

    ImageIcon tickIcon = ImageLoader.createImageIcon(ImageLoader.TICK_ICON_FILE);
    tickLabel = new JLabel(tickIcon);
    tickLabel.setToolTipText(HelpContentsPanel.createTooltipText(controller.getLocaliser().getString("showExportPrivateKeysPanel.theTwoPasswordsMatch")));
    tickLabel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    tickLabel.setVisible(false);
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 4;
    constraints.gridy = 4;
    constraints.weightx = 0.1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 3;
    constraints.anchor = GridBagConstraints.LINE_START;
    passwordProtectPanel.add(tickLabel, constraints);

    JLabel filler4 = new JLabel();
    filler4.setMinimumSize(new Dimension(3, 12));
    filler4.setMaximumSize(new Dimension(3, 12));
    filler4.setPreferredSize(new Dimension(3, 12));
    filler4.setOpaque(false);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 7;
    constraints.weightx = 0.1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    passwordProtectPanel.add(filler4, constraints);

    reminderLabel1 = new MultiBitLabel(controller.getLocaliser().getString("changePasswordPanel.reminder1"));
    reminderLabel1.setFont(reminderLabel1.getFont().deriveFont(Font.BOLD));
    reminderLabel1.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 8;
    constraints.weightx = 0.2;
    constraints.weighty = 0.3;
    constraints.gridwidth = 3;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    passwordProtectPanel.add(reminderLabel1, constraints);

    reminderLabel2 = new MultiBitLabel(controller.getLocaliser().getString("changePasswordPanel.reminder2"));
    reminderLabel2.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 9;
    constraints.weightx = 0.2;
    constraints.weighty = 0.3;
    constraints.gridwidth = 3;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    passwordProtectPanel.add(reminderLabel2, constraints);

    reminderLabel3 = new MultiBitLabel(controller.getLocaliser().getString("changePasswordPanel.reminder3"));
    reminderLabel3.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = 3;
    constraints.gridy = 10;
    constraints.weightx = 0.2;
    constraints.weighty = 0.3;
    constraints.gridwidth = 3;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.LINE_START;
    passwordProtectPanel.add(reminderLabel3, constraints);

    JLabel filler5 = new JLabel();
    filler5.setMinimumSize(new Dimension(3, 8));
    filler5.setMaximumSize(new Dimension(3, 8));
    filler5.setPreferredSize(new Dimension(3, 8));
    filler5.setOpaque(false);

    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 1;
    constraints.gridy = 11;
    constraints.weightx = 0.1;
    constraints.weighty = 0.1;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.CENTER;
    passwordProtectPanel.add(filler5, constraints);

    return passwordProtectPanel;
  }

  private JPanel createButtonPanel() {
    JPanel buttonPanel = new JPanel();
    buttonPanel.setOpaque(false);
    FlowLayout flowLayout = new FlowLayout();
    flowLayout.setAlignment(FlowLayout.RIGHT);
    buttonPanel.setLayout(flowLayout);
    buttonPanel.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));

    /**
     * Create submit action with references to the password fields - this
     * avoids having any public accessors on the panel
     */
    changePasswordSubmitAction = new ChangePasswordSubmitAction(this.bitcoinController, this,
            ImageLoader.createImageIcon(ImageLoader.CHANGE_PASSWORD_ICON_FILE), currentPasswordField, newPasswordField, repeatNewPasswordField);
    MultiBitButton submitButton = new MultiBitButton(changePasswordSubmitAction, controller);
    submitButton.setToolTipText(HelpContentsPanel.createTooltipText(controller.getLocaliser().getString("changePasswordSubmitAction.tooltip")));

    submitButton.applyComponentOrientation(ComponentOrientation.getOrientation(controller.getLocaliser().getLocale()));
    buttonPanel.add(submitButton);

    return buttonPanel;
  }

  @Override
  public void displayView(DisplayHint displayHint) {
    // If it is a wallet transaction change no need to update.
    if (DisplayHint.WALLET_TRANSACTIONS_HAVE_CHANGED == displayHint) {
      return;
    }
    walletFilenameLabel.setText(this.bitcoinController.getModel().getActiveWalletFilename());
    walletDescriptionLabel.setText(this.bitcoinController.getModel().getActivePerWalletModelData().getWalletDescription());

    walletBusyChange(this.bitcoinController.getModel().getActivePerWalletModelData().isBusy());

    clearMessages();
  }

  public void clearMessages() {
    setMessage1(" ");
    setMessage2(" ");
    setMessage3(" ");
  }

  public void clearPasswords() {
    currentPasswordField.setText("");
    newPasswordField.setText("");
    repeatNewPasswordField.setText("");
    tickLabel.setVisible(false);
  }

  public void setMessage1(String message1) {
    if (messageLabel1 != null) {
      messageLabel1.setText(message1);
    }
  }

  public void setMessage2(String message2) {
    if (messageLabel2 != null) {
      messageLabel2.setText(message2);
    }
  }

  public void setMessage3(String message3) {
    if (messageLabel3 != null) {
      messageLabel3.setText(message3);
    }
  }

  class PasswordListener implements KeyListener {
    /**
     * Handle the key typed event from the text field.
     */
    @Override
    public void keyTyped(KeyEvent e) {
    }

    /**
     * Handle the key-pressed event from the text field.
     */
    @Override
    public void keyPressed(KeyEvent e) {
      // do nothing
    }

    /**
     * Handle the key-released event from the text field.
     */
    @Override
    public void keyReleased(KeyEvent e) {
      char[] password1 = null;
      char[] password2 = null;

      if (newPasswordField != null) {
        password1 = newPasswordField.getPassword();
      }
      if (repeatNewPasswordField != null) {
        password2 = repeatNewPasswordField.getPassword();
      }

      boolean tickLabelVisible = false;
      if (password1 != null && password2 != null) {
        if (Arrays.equals(password1, password2)) {
          tickLabelVisible = true;
        }
      }
      tickLabel.setVisible(tickLabelVisible);

      clearMessages();

      // clear the password arrays
      for (int i = 0; i < password1.length; i++) {
        password1[i] = 0;
      }

      for (int i = 0; i < password2.length; i++) {
        password2[i] = 0;
      }
    }
  }

  @Override
  public Icon getViewIcon() {
    return ImageLoader.createImageIcon(ImageLoader.CHANGE_PASSWORD_ICON_FILE);
  }

  @Override
  public String getViewTitle() {
    return controller.getLocaliser().getString("changePasswordAction.text");
  }

  @Override
  public String getViewTooltip() {
    return controller.getLocaliser().getString("changePasswordAction.tooltip");
  }

  @Override
  public View getViewId() {
    return View.CHANGE_PASSWORD_VIEW;
  }

  @Override
  public final void walletBusyChange(boolean newWalletIsBusy) {
    boolean unencryptedWalletType = this.bitcoinController.getModel().getActiveWallet() == null ? false : this.bitcoinController.getModel().getActiveWallet().getEncryptionType() == EncryptionType.UNENCRYPTED;

    if (unencryptedWalletType) {
      // Is an unencrypted wallet so cannot change a password regardless.
      changePasswordSubmitAction.putValue(Action.SHORT_DESCRIPTION,
              controller.getLocaliser().getString("changePasswordSubmitAction.text"));
      changePasswordSubmitAction.setEnabled(false);
    } else {
      // Update the enable status of the action to match the wallet busy
      // status.
      if (this.bitcoinController.getModel().getActivePerWalletModelData().isBusy()) {
        // Wallet is busy with another operation that may change the
        // private keys - Action is disabled.
        changePasswordSubmitAction.putValue(
                Action.SHORT_DESCRIPTION,
                controller.getLocaliser().getString("multiBitSubmitAction.walletIsBusy",
                        new Object[]{controller.getLocaliser().getString(this.bitcoinController.getModel().getActivePerWalletModelData().getBusyTaskKey())}));
        changePasswordSubmitAction.setEnabled(false);
      } else {
        // Enable
        changePasswordSubmitAction.putValue(Action.SHORT_DESCRIPTION,
                controller.getLocaliser().getString("changePasswordSubmitAction.text"));

        changePasswordSubmitAction.setEnabled(true);
      }
    }
  }
}