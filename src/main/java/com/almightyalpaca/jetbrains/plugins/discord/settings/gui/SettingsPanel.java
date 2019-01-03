/*
 * Copyright 2017-2018 Aljoscha Grebe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.almightyalpaca.jetbrains.plugins.discord.settings.gui;

import com.almightyalpaca.jetbrains.plugins.discord.components.ApplicationComponent;
import com.almightyalpaca.jetbrains.plugins.discord.debug.Debug;
import com.almightyalpaca.jetbrains.plugins.discord.debug.Logger;
import com.almightyalpaca.jetbrains.plugins.discord.debug.LoggerFactory;
import com.almightyalpaca.jetbrains.plugins.discord.settings.ApplicationSettings;
import com.almightyalpaca.jetbrains.plugins.discord.settings.ProjectSettings;
import com.almightyalpaca.jetbrains.plugins.discord.settings.gui.themes.ThemeChooser;
import com.almightyalpaca.jetbrains.plugins.discord.themes.Theme;
import com.almightyalpaca.jetbrains.plugins.discord.themes.ThemeLoader;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

public class SettingsPanel
{
    @NotNull
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationComponent.class);

    private final ApplicationSettings applicationSettings;
    private final ProjectSettings projectSettings;
    private JPanel panelRoot;
    private JPanel panelProject;
    private JBCheckBox projectEnabled;
    private JPanel panelApplication;
    private JBCheckBox applicationEnabled;
    private JBCheckBox applicationUnknownImageIDE;
    private JBCheckBox applicationUnknownImageFile;
    private JBCheckBox applicationShowFileExtensions;
    private JBCheckBox applicationHideReadOnlyFiles;
    private JBCheckBox applicationShowReadingInsteadOfEditing;
    private JBCheckBox applicationShowIDEWhenNoProjectIsAvailable;
    private JBCheckBox applicationHideAfterPeriodOfInactivity;
    private JSpinner applicationInactivityTimeout;
    private JLabel applicationInactivityTimeoutLabel;
    private JBCheckBox applicationResetOpenTimeAfterInactivity;
    private JPanel panelExperimental;
    private JBCheckBox applicationExperimentalWindowListenerEnabled;
    private JPanel panelDebug;
    private JBCheckBox applicationDebugLoggingEnabled;
    private TextFieldWithBrowseButton applicationDebugLogFolder;
    private JButton buttonDumpCurrentState;
    private JButton buttonOpenDebugLogFolder;
    private JBCheckBox applicationShowFiles;
    private JBTextField projectDescription;
    private JBCheckBox applicationShowElapsedTime;
    private JBCheckBox applicationForceBigIDEIcon;
    private Theme applicationTheme;
    private JButton applicationThemeButton;
    private JBLabel applicationThemeLabel;

    public SettingsPanel(ApplicationSettings applicationSettings, ProjectSettings projectSettings)
    {
        this.applicationSettings = applicationSettings;
        this.projectSettings = projectSettings;

        $$$setupUI$$$();
        this.panelProject.setBorder(IdeBorderFactory.createTitledBorder(
                "Project Settings (" + projectSettings.getProject().getName() + ")"));
        this.panelApplication.setBorder(IdeBorderFactory.createTitledBorder("Application Settings"));
        this.panelExperimental.setBorder(IdeBorderFactory.createTitledBorder("Experimental Settings"));
        this.panelDebug.setBorder(IdeBorderFactory.createTitledBorder("Debugging Settings"));

        PlainDocument document = new PlainDocument();
        document.setDocumentFilter(new DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException
            {
                if (bypass.getDocument().getLength() + text.length() <= 128)
                    super.insertString(bypass, offset, text, attributes);
            }

            @Override
            public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
                    throws BadLocationException
            {
                if (bypass.getDocument().getLength() - length + text.length() <= 128)
                    super.replace(bypass, offset, length, text, attributes);
            }
        });
        this.projectDescription.setDocument(document);

        this.applicationHideReadOnlyFiles.addItemListener(e -> this.updateButtons());
        this.applicationHideAfterPeriodOfInactivity.addItemListener(e -> this.updateButtons());
        this.applicationDebugLoggingEnabled.addItemListener(e -> this.updateButtons());
        this.applicationShowFiles.addItemListener(e -> updateButtons());

        this.applicationDebugLogFolder.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));
        this.applicationDebugLogFolder.getTextField().getDocument().addDocumentListener(new DocumentAdapter()
        {
            @Override
            protected void textChanged(DocumentEvent e)
            {
                verifyLogFolder();
            }
        });

        this.buttonDumpCurrentState.addActionListener(e -> Debug.printDebugInfo(this.applicationDebugLogFolder.getText()));
        this.buttonOpenDebugLogFolder.addActionListener(e -> {
            try
            {
                if (verifyLogFolder())
                    Desktop.getDesktop().open(createFolder(applicationDebugLogFolder.getText()).toFile());
            }
            catch (Exception ex)
            {
                LOG.error("An error occurred while trying to open the debug log folder", ex);
            }
        });

        this.applicationThemeButton.addActionListener(e -> SwingUtilities.invokeLater(() -> new ThemeChooser(this).show()));
    }

    public boolean isModified()
    {
        // @formatter:off
        return verifyLogFolder() &&
                (this.projectEnabled.isSelected() != this.projectSettings.getState().isEnabled()
                        || this.applicationEnabled.isSelected() != this.applicationSettings.getState().isEnabled()
                        || this.applicationUnknownImageIDE.isSelected() != this.applicationSettings.getState().isShowUnknownImageIDE()
                        || this.applicationUnknownImageFile.isSelected() != this.applicationSettings.getState().isShowUnknownImageFile()
                        || this.applicationShowFileExtensions.isSelected() != this.applicationSettings.getState().isShowFileExtensions()
                        || this.applicationHideReadOnlyFiles.isSelected() != this.applicationSettings.getState().isHideReadOnlyFiles()
                        || this.applicationShowReadingInsteadOfEditing.isSelected() != this.applicationSettings.getState().isShowReadingInsteadOfWriting()
                        || this.applicationShowIDEWhenNoProjectIsAvailable.isSelected() != this.applicationSettings.getState().isShowIDEWhenNoProjectIsAvailable()
                        || this.applicationHideAfterPeriodOfInactivity.isSelected() != this.applicationSettings.getState().isHideAfterPeriodOfInactivity()
                        || (long) this.applicationInactivityTimeout.getValue() != this.applicationSettings.getState().getInactivityTimeout(TimeUnit.MINUTES)
                        || this.applicationResetOpenTimeAfterInactivity.isSelected() != this.applicationSettings.getState().isResetOpenTimeAfterInactivity()
                        || this.applicationExperimentalWindowListenerEnabled.isSelected() != this.applicationSettings.getState().isExperimentalWindowListenerEnabled()
                        || this.applicationDebugLoggingEnabled.isSelected() != this.applicationSettings.getState().isDebugLoggingEnabled()
                        || !Objects.equals(this.applicationDebugLogFolder.getText(), this.applicationSettings.getState().getDebugLogFolder()))
                || this.applicationShowFiles.isSelected() != this.applicationSettings.getState().isShowFiles()
                || !Objects.equals(this.projectDescription.getText(), this.projectSettings.getState().getDescription())
                || this.applicationShowElapsedTime.isSelected() != this.applicationSettings.getState().isShowElapsedTime()
                || this.applicationForceBigIDEIcon.isSelected() != this.applicationSettings.getState().isForceBigIDEIcon()
                || !Objects.equals(this.applicationTheme, this.applicationSettings.getState().getTheme());
        // @formatter:on
    }

    public void apply()
    {
        this.projectSettings.getState().setEnabled(this.projectEnabled.isSelected());
        this.projectSettings.getState().setDescription(this.projectDescription.getText());
        this.applicationSettings.getState().setEnabled(this.applicationEnabled.isSelected());
        this.applicationSettings.getState().setShowUnknownImageIDE(this.applicationUnknownImageIDE.isSelected());
        this.applicationSettings.getState().setShowUnknownImageFile(this.applicationUnknownImageFile.isSelected());
        this.applicationSettings.getState().setShowFileExtensions(this.applicationShowFileExtensions.isSelected());
        this.applicationSettings.getState().setHideReadOnlyFiles(this.applicationHideReadOnlyFiles.isSelected());
        this.applicationSettings.getState().setShowReadingInsteadOfWriting(this.applicationShowReadingInsteadOfEditing.isSelected());
        this.applicationSettings
                .getState()
                .setShowIDEWhenNoProjectIsAvailable(this.applicationShowIDEWhenNoProjectIsAvailable.isSelected());
        this.applicationSettings.getState().setHideAfterPeriodOfInactivity(this.applicationHideAfterPeriodOfInactivity.isSelected());
        this.applicationSettings.getState().setInactivityTimeout((long) this.applicationInactivityTimeout.getValue(), TimeUnit.MINUTES);
        this.applicationSettings
                .getState()
                .setExperimentalWindowListenerEnabled(this.applicationExperimentalWindowListenerEnabled.isSelected());
        this.applicationSettings.getState().setResetOpenTimeAfterInactivity(this.applicationResetOpenTimeAfterInactivity.isSelected());
        this.applicationSettings.getState().setDebugLoggingEnabled(this.applicationDebugLoggingEnabled.isSelected());
        this.applicationSettings.getState().setShowFiles(this.applicationShowFiles.isSelected());
        this.applicationSettings.getState().setShowElapsedTime(this.applicationShowElapsedTime.isSelected());
        this.applicationSettings.getState().setForceBigIDEIcon(this.applicationForceBigIDEIcon.isSelected());
        this.applicationSettings.getState().setTheme(this.applicationTheme);

        if (verifyLogFolder())
            this.applicationSettings
                    .getState()
                    .setDebugLogFolder(createFolder(this.applicationDebugLogFolder.getText()).toAbsolutePath().toString());
    }

    public void reset()
    {
        System.out.println("RESET");

        this.projectEnabled.setSelected(this.projectSettings.getState().isEnabled());
        this.projectDescription.setText(this.projectSettings.getState().getDescription());
        this.applicationEnabled.setSelected(this.applicationSettings.getState().isEnabled());
        this.applicationUnknownImageIDE.setSelected(this.applicationSettings.getState().isShowUnknownImageIDE());
        this.applicationUnknownImageFile.setSelected(this.applicationSettings.getState().isShowUnknownImageFile());
        this.applicationShowFileExtensions.setSelected(this.applicationSettings.getState().isShowFileExtensions());
        this.applicationHideReadOnlyFiles.setSelected(this.applicationSettings.getState().isHideReadOnlyFiles());
        this.applicationShowReadingInsteadOfEditing.setSelected(this.applicationSettings.getState().isShowReadingInsteadOfWriting());
        this.applicationShowIDEWhenNoProjectIsAvailable.setSelected(this.applicationSettings
                .getState()
                .isShowIDEWhenNoProjectIsAvailable());
        this.applicationHideAfterPeriodOfInactivity.setSelected(this.applicationSettings.getState().isHideAfterPeriodOfInactivity());
        this.applicationInactivityTimeout.setValue(this.applicationSettings.getState().getInactivityTimeout(TimeUnit.MINUTES));
        this.applicationResetOpenTimeAfterInactivity.setSelected(this.applicationSettings.getState().isResetOpenTimeAfterInactivity());
        this.applicationExperimentalWindowListenerEnabled.setSelected(this.applicationSettings
                .getState()
                .isExperimentalWindowListenerEnabled());
        this.applicationDebugLoggingEnabled.setSelected(this.applicationSettings.getState().isDebugLoggingEnabled());
        this.applicationDebugLogFolder.setText(this.applicationSettings.getState().getDebugLogFolder());
        this.applicationShowFiles.setSelected(this.applicationSettings.getState().isShowFiles());
        this.applicationShowElapsedTime.setSelected(this.applicationSettings.getState().isShowElapsedTime());
        this.applicationForceBigIDEIcon.setSelected(this.applicationSettings.getState().isForceBigIDEIcon());
        this.setTheme(this.applicationSettings.getState().getTheme());

        this.updateButtons();
    }

    public void updateButtons()
    {
        this.applicationInactivityTimeoutLabel.setEnabled(this.applicationHideAfterPeriodOfInactivity.isSelected());
        this.applicationInactivityTimeout.setEnabled(this.applicationHideAfterPeriodOfInactivity.isSelected());
        this.applicationResetOpenTimeAfterInactivity.setEnabled(this.applicationHideAfterPeriodOfInactivity.isSelected());

        this.applicationUnknownImageFile.setEnabled(this.applicationShowFiles.isSelected());
        this.applicationShowFileExtensions.setEnabled(this.applicationShowFiles.isSelected());
        this.applicationHideReadOnlyFiles.setEnabled(this.applicationShowFiles.isSelected());

        this.applicationShowReadingInsteadOfEditing.setEnabled(
                this.applicationShowFiles.isSelected() && !this.applicationHideReadOnlyFiles.isSelected());

        this.applicationDebugLogFolder.setEnabled(this.applicationDebugLoggingEnabled.isSelected());

        this.verifyLogFolder();
    }

    private boolean verifyLogFolder()
    {
        Path path;
        try
        {
            path = Paths.get(this.applicationDebugLogFolder.getText());
        }
        catch (Exception e)
        {
            this.applicationDebugLogFolder.getTextField().setForeground(JBColor.RED);
            this.applicationDebugLogFolder.getTextField().setComponentPopupMenu(new JBPopupMenu("Invalid path"));
            this.buttonDumpCurrentState.setEnabled(false);
            this.buttonOpenDebugLogFolder.setEnabled(false);

            return false;
        }

        if (Files.isRegularFile(path))
        {
            this.applicationDebugLogFolder.getTextField().setForeground(JBColor.RED);
            this.applicationDebugLogFolder.getTextField().setComponentPopupMenu(new JBPopupMenu("Path is a file"));
            this.buttonDumpCurrentState.setEnabled(false);
            this.buttonOpenDebugLogFolder.setEnabled(false);

            return false;
        }

        if (!Files.isWritable(path))
        {
            this.applicationDebugLogFolder.getTextField().setForeground(JBColor.RED);
            this.applicationDebugLogFolder.getTextField().setComponentPopupMenu(new JBPopupMenu("Cannot write to this path"));
            this.buttonOpenDebugLogFolder.setEnabled(false);
        }

        this.applicationDebugLogFolder.getTextField().setForeground(JBColor.foreground());
        this.applicationDebugLogFolder.getTextField().setComponentPopupMenu(null);
        this.buttonDumpCurrentState.setEnabled(applicationDebugLoggingEnabled.isSelected());
        this.buttonOpenDebugLogFolder.setEnabled(true);

        if (!Files.exists(path))
        {
            this.buttonDumpCurrentState.setEnabled(false);
            this.buttonOpenDebugLogFolder.setEnabled(false);
        }

        return true;
    }

    private Path createFolder(@NotNull String path)
    {
        return createFolder(Paths.get(path));
    }

    private Path createFolder(@NotNull Path path)
    {
        if (this.applicationDebugLoggingEnabled.isSelected() && verifyLogFolder() && !Files.isDirectory(path))
        {
            try
            {
                Files.createDirectories(path);
            }
            catch (IOException e)
            {
                LOG.warn("Could not create folder", e);
            }
        }

        return path;
    }

    @NotNull
    public JPanel getRootPanel()
    {
        return this.panelRoot;
    }

    private void createUIComponents()
    {
        Long timeoutValue = 1L;
        Long timeoutMin = 1L;
        Long timeoutMax = TimeUnit.MINUTES.convert(1, TimeUnit.DAYS);
        Long timeoutStepSize = 1L;

        this.applicationInactivityTimeout = new JSpinner(new SpinnerNumberModel(timeoutValue, timeoutMin, timeoutMax, timeoutStepSize));
    }

    @NotNull
    public SortedMap<String, Theme> getThemes()
    {
        return ThemeLoader.getInstance().getThemes();
    }

    @NotNull
    public Theme getThemeById(@NotNull String name)
    {
        return Optional.ofNullable(getThemes().get(name))
                .orElse(getThemes().get("Classic"));
    }

    public Theme getTheme()
    {
        return this.applicationTheme;
    }

    public void setTheme(@NotNull Theme theme)
    {
        this.applicationTheme = theme;

        this.applicationThemeLabel.setText("<html><b>" + theme.getName() + "</b></html>");
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$()
    {
        createUIComponents();
        panelRoot = new JPanel();
        panelRoot.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelProject = new JPanel();
        panelProject.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelRoot.add(panelProject, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelProject.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        projectEnabled = new JBCheckBox();
        projectEnabled.setEnabled(true);
        projectEnabled.setText("Enabled");
        panel1.add(projectEnabled, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelProject.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, 1, 1, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setEnabled(true);
        label1.setText("Description (max. 128 chars):");
        panel2.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        projectDescription = new JBTextField();
        projectDescription.setColumns(0);
        projectDescription.setText("");
        panel2.add(projectDescription, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panelApplication = new JPanel();
        panelApplication.setLayout(new GridLayoutManager(13, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelRoot.add(panelApplication, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel3.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationEnabled = new JBCheckBox();
        applicationEnabled.setEnabled(true);
        applicationEnabled.setText("Enabled");
        panel3.add(applicationEnabled, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel4.add(spacer3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationUnknownImageIDE = new JBCheckBox();
        applicationUnknownImageIDE.setEnabled(true);
        applicationUnknownImageIDE.setText("Show image for unknown IDEs");
        panel4.add(applicationUnknownImageIDE, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel5, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel5.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationUnknownImageFile = new JBCheckBox();
        applicationUnknownImageFile.setEnabled(true);
        applicationUnknownImageFile.setText("Show image for unknown files");
        panel5.add(applicationUnknownImageFile, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel6, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel6.add(spacer5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationShowFileExtensions = new JBCheckBox();
        applicationShowFileExtensions.setEnabled(true);
        applicationShowFileExtensions.setText("Show file extensions");
        panel6.add(applicationShowFileExtensions, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel7, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        panel7.add(spacer6, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationHideReadOnlyFiles = new JBCheckBox();
        applicationHideReadOnlyFiles.setEnabled(true);
        applicationHideReadOnlyFiles.setText("Hide read-only files");
        panel7.add(applicationHideReadOnlyFiles, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel8, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer7 = new Spacer();
        panel8.add(spacer7, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationShowReadingInsteadOfEditing = new JBCheckBox();
        applicationShowReadingInsteadOfEditing.setEnabled(true);
        applicationShowReadingInsteadOfEditing.setText("Show \"Reading\" instead of \"Writing\" for read-only files");
        panel8.add(applicationShowReadingInsteadOfEditing, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 6, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel9, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer8 = new Spacer();
        panel9.add(spacer8, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationShowIDEWhenNoProjectIsAvailable = new JBCheckBox();
        applicationShowIDEWhenNoProjectIsAvailable.setEnabled(true);
        applicationShowIDEWhenNoProjectIsAvailable.setText("Show IDE when no open or enabled project is available");
        panel9.add(applicationShowIDEWhenNoProjectIsAvailable, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel10, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        applicationHideAfterPeriodOfInactivity = new JBCheckBox();
        applicationHideAfterPeriodOfInactivity.setEnabled(true);
        applicationHideAfterPeriodOfInactivity.setText("Hide Rich Presence after a period of inactivity");
        panel10.add(applicationHideAfterPeriodOfInactivity, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        applicationInactivityTimeoutLabel = new JLabel();
        applicationInactivityTimeoutLabel.setText("Timeout (minutes):");
        panel10.add(applicationInactivityTimeoutLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
        panel10.add(applicationInactivityTimeout, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer9 = new Spacer();
        panel10.add(spacer9, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel11, new GridConstraints(11, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer10 = new Spacer();
        panel11.add(spacer10, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationResetOpenTimeAfterInactivity = new JBCheckBox();
        applicationResetOpenTimeAfterInactivity.setEnabled(true);
        applicationResetOpenTimeAfterInactivity.setText("Reset open time on timeout");
        panel11.add(applicationResetOpenTimeAfterInactivity, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel12, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer11 = new Spacer();
        panel12.add(spacer11, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationShowFiles = new JBCheckBox();
        applicationShowFiles.setEnabled(true);
        applicationShowFiles.setText("Show files");
        panel12.add(applicationShowFiles, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel13, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer12 = new Spacer();
        panel13.add(spacer12, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationShowElapsedTime = new JBCheckBox();
        applicationShowElapsedTime.setEnabled(true);
        applicationShowElapsedTime.setText("Show elapsed time");
        panel13.add(applicationShowElapsedTime, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel14, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer13 = new Spacer();
        panel14.add(spacer13, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationForceBigIDEIcon = new JBCheckBox();
        applicationForceBigIDEIcon.setEnabled(true);
        applicationForceBigIDEIcon.setText("Force big IDE icon");
        panel14.add(applicationForceBigIDEIcon, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        panelApplication.add(panel15, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JBLabel jBLabel1 = new JBLabel();
        jBLabel1.setText("Theme:");
        panel15.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        applicationThemeLabel = new JBLabel();
        applicationThemeLabel.setText("");
        panel15.add(applicationThemeLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer14 = new Spacer();
        panel15.add(spacer14, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationThemeButton = new JButton();
        applicationThemeButton.setText("Change");
        panel15.add(applicationThemeButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 3, false));
        final Spacer spacer15 = new Spacer();
        panelRoot.add(spacer15, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 40), null, null, 0, false));
        panelExperimental = new JPanel();
        panelExperimental.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelRoot.add(panelExperimental, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panelExperimental.add(panel16, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer16 = new Spacer();
        panel16.add(spacer16, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationExperimentalWindowListenerEnabled = new JBCheckBox();
        applicationExperimentalWindowListenerEnabled.setEnabled(true);
        applicationExperimentalWindowListenerEnabled.setText("Enable experimental window listener (updates a file when you scroll or resize an editor window)");
        panel16.add(applicationExperimentalWindowListenerEnabled, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panelDebug = new JPanel();
        panelDebug.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        panelRoot.add(panelDebug, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        applicationDebugLoggingEnabled = new JBCheckBox();
        applicationDebugLoggingEnabled.setEnabled(true);
        applicationDebugLoggingEnabled.setText("Enable Debugging");
        panelDebug.add(applicationDebugLoggingEnabled, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Debug log folder:");
        panelDebug.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
        final Spacer spacer17 = new Spacer();
        panelDebug.add(spacer17, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        applicationDebugLogFolder = new TextFieldWithBrowseButton();
        panelDebug.add(applicationDebugLogFolder, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonDumpCurrentState = new JButton();
        buttonDumpCurrentState.setText("Dump current state to debug log file");
        panelDebug.add(buttonDumpCurrentState, new GridConstraints(0, 2, 1, 2, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 3, false));
        buttonOpenDebugLogFolder = new JButton();
        buttonOpenDebugLogFolder.setText("Open");
        panelDebug.add(buttonOpenDebugLogFolder, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() { return panelRoot; }
}
