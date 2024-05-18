package com.bytedance.tools.codelocator.dialog

import com.android.ddmlib.AndroidDebugBridge
import com.bytedance.tools.codelocator.action.SimpleAction
import com.bytedance.tools.codelocator.device.Device
import com.bytedance.tools.codelocator.device.DeviceManager
import com.bytedance.tools.codelocator.device.DeviceManager.OnExecutedListener
import com.bytedance.tools.codelocator.device.action.AdbCommand
import com.bytedance.tools.codelocator.device.action.BroadcastAction
import com.bytedance.tools.codelocator.exception.ExecuteException
import com.bytedance.tools.codelocator.listener.OnActionListener
import com.bytedance.tools.codelocator.listener.OnClickListener
import com.bytedance.tools.codelocator.model.CodeLocatorUserConfig
import com.bytedance.tools.codelocator.panels.CodeLocatorWindow
import com.bytedance.tools.codelocator.utils.CoordinateUtils
import com.bytedance.tools.codelocator.utils.IdeaUtils
import com.bytedance.tools.codelocator.utils.JComponentUtils
import com.bytedance.tools.codelocator.utils.Mob
import com.bytedance.tools.codelocator.utils.NotificationUtils
import com.bytedance.tools.codelocator.utils.OSHelper
import com.bytedance.tools.codelocator.utils.ResUtils
import com.bytedance.tools.codelocator.utils.StringUtils
import com.bytedance.tools.codelocator.utils.ThreadUtils
import com.bytedance.tools.codelocator.response.StringResponse
import com.bytedance.tools.codelocator.utils.CodeLocatorConstants.ACTION_PROCESS_CONFIG_LIST
import com.bytedance.tools.codelocator.utils.CodeLocatorConstants.KEY_ACTION_CLEAR
import com.bytedance.tools.codelocator.utils.CodeLocatorConstants.KEY_CODELOCATOR_ACTION
import com.bytedance.tools.codelocator.utils.NetUtils
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ex.WindowManagerEx
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JPanel

class EditSettingsDialog(val codeLocatorWindow: CodeLocatorWindow, val project: Project) :
    JDialog(WindowManagerEx.getInstance().getFrame(project), ModalityType.MODELESS) {

    companion object {

        const val DIALOG_WIDTH = 420

    }

    var config: CodeLocatorUserConfig = CodeLocatorUserConfig.loadConfig()

    lateinit var dialogContentPanel: JPanel

    init {
        initContentPanel()
        addEditPortView()
    }

    private fun initContentPanel() {
        title = "CodeLocator " + ResUtils.getString("settings")
        dialogContentPanel = JPanel()
        dialogContentPanel.border = BorderFactory.createEmptyBorder(
            CoordinateUtils.DEFAULT_BORDER,
            CoordinateUtils.DEFAULT_BORDER,
            CoordinateUtils.DEFAULT_BORDER,
            CoordinateUtils.DEFAULT_BORDER
        )
        dialogContentPanel.layout = BoxLayout(dialogContentPanel, BoxLayout.Y_AXIS)
        contentPane = dialogContentPanel
    }

    private fun addChooseDeviceBox() {
        val currentDevice = DeviceManager.getCurrentDevice(project)
        var currentDevStr = ""
        if (currentDevice != null) {
            currentDevStr = ResUtils.getString("current_device_format", currentDevice.deviceName)
        }
        val chooseDevicesBtn = JButton(currentDevStr)
        chooseDevicesBtn.font = Font(chooseDevicesBtn.font.name, chooseDevicesBtn.font.style, 15)
        chooseDevicesBtn.addActionListener {
            Mob.mob(Mob.Action.CLICK, "switch_device")
            val devices = AndroidDebugBridge.getBridge().devices
            val actionGroup = DefaultActionGroup("listGroup", true)
            for (device in devices) {
                if (device.isOnline) {
                    actionGroup.add(SimpleAction(device.name, object : OnActionListener {
                        override fun actionPerformed(e: AnActionEvent) {
                            DeviceManager.setCurrentSelectedDevice(project, device)
                            chooseDevicesBtn.text = ResUtils.getString("current_device_format", device.name)
                        }
                    }))
                }
            }
            val popDialog = JBPopupFactory.getInstance().createActionGroupPopup(
                ResUtils.getString("switch_device"),
                actionGroup,
                DataManager.getInstance().dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )
            popDialog.showCenteredInCurrentWindow(project)
        }
        dialogContentPanel.add(chooseDevicesBtn)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addEditPortView() {
        addChooseDeviceBox()
        addEnglishChangeSetBox()
        addViewChangeSetBox()
        if (NetUtils.SEARCH_CODE_URL.isNotEmpty()) {
            addJumpPageSetBox()
        }
        addJumpPageBranchBox()
        addSchemaCloseSettingBox()
        addAdjustPanelHeightBox()
        addShowViewLevelBox()
        addDrawViewSizeBox()
        addDrawViewPaddingBox()
        addOpenCharlesBox()
        addMouseWheelBox()
        addPreviewColorBox()

        if (NetUtils.SEARCH_CODE_URL.isNotEmpty()) {
            addSearchCodeIndexBox()
        }

        addAsyncBroadcastBox()
        addSystemAdb()
        addSystemImageEditor()
        if (IdeaUtils.getVersionStr() != null && IdeaUtils.getVersionInt() >= 2021001001) {
            addUseSupportLibraryCheck()
        }
        addEnableVoiceBox()
        clearConfigListBox()
        addConfigButton()

        val componentCount = dialogContentPanel.componentCount
        var panelHeight = 0
        for (i in 0 until componentCount) {
            val component = dialogContentPanel.getComponent(i)
            var h = component.preferredSize.height
            panelHeight += h
        }
        dialogContentPanel.minimumSize = Dimension(DIALOG_WIDTH, panelHeight + CoordinateUtils.DEFAULT_BORDER * 2)
        dialogContentPanel.preferredSize = dialogContentPanel.minimumSize
        preferredSize = dialogContentPanel.preferredSize
        minimumSize = preferredSize
        setLocationRelativeTo(WindowManagerEx.getInstance().getFrame(project))
        JComponentUtils.supportCommandW(dialogContentPanel, object : OnClickListener {
            override fun onClick() {
                hide()
            }
        })
    }

    override fun show() {
        super.show()
        OSHelper.instance.adjustDialog(this, project)
    }

    private fun addJumpPageSetBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("go_to_blame"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isJumpToBlamePage
        jCheckBox.addItemListener {
            config.isJumpToBlamePage = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isJumpToBlamePage) Mob.Button.OPEN_JUMP_BLAME_TAB else Mob.Button.CLOSE_JUMP_BLAME_TAB
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addSchemaCloseSettingBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("close_dialog_after_send_schema"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isCloseDialogWhenSchemaSend
        jCheckBox.addItemListener {
            config.isCloseDialogWhenSchemaSend = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isCloseDialogWhenSchemaSend) Mob.Button.OPEN_DIALOG_WHEN_SCHEMA_SEND else Mob.Button.CLOSE_DIALOG_WHEN_SCHEMA_SEND
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addJumpPageBranchBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("follow_branch"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isJumpToCurrentBranch
        jCheckBox.addItemListener {
            config.isJumpToCurrentBranch = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isJumpToCurrentBranch) Mob.Button.OPEN_JUMP_BLAME_WITH_BRANCH else Mob.Button.CLOSE_JUMP_BLAME_WITH_BRANCH
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addViewChangeSetBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("change_to_view_tab"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isChangeTabWhenViewChange
        jCheckBox.addItemListener {
            config.isChangeTabWhenViewChange = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isChangeTabWhenViewChange) Mob.Button.OPEN_VIEW_CHANGE_TAB else Mob.Button.CLOSE_VIEW_CHANGE_TAB
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addEnglishChangeSetBox() {
        val jCheckBox = JCheckBox("English (Need Restart)")
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected =
            if (config.res.isNullOrEmpty()) !Locale.getDefault().language.contains("zh") else config.res.equals("en")
        jCheckBox.addItemListener {
            config.res = if (it.stateChange == ItemEvent.SELECTED) "en" else "zh"
            Mob.mob(
                Mob.Action.CLICK,
                config.res
            )
            ResUtils.setCurrentRes(config.res)
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addAdjustPanelHeightBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("view_tree_height_follow_plugin"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isCanAdjustPanelHeight
        jCheckBox.addItemListener {
            config.isCanAdjustPanelHeight = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isCanAdjustPanelHeight) Mob.Button.OPEN_HEIGHT_CHANGE_TAB else Mob.Button.CLOSE_HEIGHT_CHANGE_TAB
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addShowViewLevelBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("show_view_deep"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isShowViewLevel
        jCheckBox.addItemListener {
            config.isShowViewLevel = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isShowViewLevel) Mob.Button.OPEN_SHOW_VIEW_LEVEL else Mob.Button.CLOSE_SHOW_VIEW_LEVEL
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addDrawViewSizeBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("draw_view_size"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isDrawViewSize
        jCheckBox.addItemListener {
            config.isDrawViewSize = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isDrawViewSize) "open_draw_size" else "close_draw_size"
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addDrawViewPaddingBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("draw_view_padding"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isDrawViewPadding
        jCheckBox.addItemListener {
            config.isDrawViewPadding = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isDrawViewPadding) "open_draw_padding" else "close_draw_padding"
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addOpenCharlesBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("auto_open_charles"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isAutoOpenCharles
        jCheckBox.addItemListener {
            config.isAutoOpenCharles = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isAutoOpenCharles) "auto_open_charles" else "close_auto_open"
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addMouseWheelBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("set_scroll"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isMouseWheelDirection
        jCheckBox.addItemListener {
            config.isMouseWheelDirection = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isMouseWheelDirection) Mob.Button.OPEN_MOUSE_WHEEL else Mob.Button.CLOSE_MOUSE_WHEEL
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addPreviewColorBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("preview_color"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isPreviewColor
        jCheckBox.addItemListener {
            config.isPreviewColor = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isPreviewColor) Mob.Button.OPEN_PREVIEW else Mob.Button.CLOSE_PREVIEW
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addSearchCodeIndexBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("enable_code_index"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isShowSearchCodeIndex
        jCheckBox.addItemListener {
            config.isShowSearchCodeIndex = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isShowSearchCodeIndex) Mob.Button.OPEN_CODE_INDEX else Mob.Button.CLOSE_CODE_INDEX
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addAsyncBroadcastBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("support_async_broadcast"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isAsyncBroadcast
        jCheckBox.addItemListener {
            config.isAsyncBroadcast = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isAsyncBroadcast) "open_async_broadcast" else "close_async_broadcast"
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addUseSupportLibraryCheck() {
        val supportLib = CodeLocatorUserConfig.loadConfig().getSupportLib(project)
        val jCheckBox = JCheckBox("useSupportLibrary")
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = supportLib
        jCheckBox.addItemListener {
            config.setSupportLib(project, (it.stateChange == ItemEvent.SELECTED))
            Mob.mob(
                Mob.Action.CLICK,
                if ((it.stateChange == ItemEvent.SELECTED)) "OPEN_USESUPPORTLIBRARY" else "CLOSE_USESUPPORTLIBRARY"
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addSystemAdb() {
        val jCheckBox = JCheckBox(ResUtils.getString("use_default_adb"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isUseDefaultAdb
        jCheckBox.addItemListener {
            config.isUseDefaultAdb = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isUseDefaultAdb) "OPEN_DEFAULT_ADB" else "CLOSE_DEFAULT_ADB"
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addSystemImageEditor() {
        val jCheckBox = JCheckBox(ResUtils.getString("opens_pic_with_editor"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isUseImageEditor
        jCheckBox.addItemListener {
            config.isUseImageEditor = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isUseImageEditor) "OPEN_IMAGE_EDITOR" else "CLOSE_IMAGE_EDITOR"
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addEnableVoiceBox() {
        val jCheckBox = JCheckBox(ResUtils.getString("enable_install_voice"))
        jCheckBox.font = Font(jCheckBox.font.name, jCheckBox.font.style, 15)
        jCheckBox.isSelected = config.isEnableVoice
        jCheckBox.addItemListener {
            config.isEnableVoice = (it.stateChange == ItemEvent.SELECTED)
            Mob.mob(
                Mob.Action.CLICK,
                if (config.isEnableVoice) Mob.Button.OPEN_VOICE else Mob.Button.CLOSE_VOICE
            )
        }
        dialogContentPanel.add(jCheckBox)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun clearConfigListBox() {
        val clearButton = JButton(ResUtils.getString("config_clear_jump_list"))
        clearButton.font = Font(clearButton.font.name, clearButton.font.style, 15)
        clearButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                super.mouseClicked(e)
                DeviceManager.enqueueCmd(project,
                    AdbCommand(
                        BroadcastAction(
                            ACTION_PROCESS_CONFIG_LIST
                        ).args(KEY_CODELOCATOR_ACTION, KEY_ACTION_CLEAR)
                    ),
                    StringResponse::class.java,
                    object : OnExecutedListener<StringResponse> {

                        override fun onExecSuccess(device: Device, response: StringResponse) {
                            if ("true".equals(response.data, true)) {
                                ThreadUtils.runOnUIThread {
                                    NotificationUtils.showNotifyInfoShort(
                                        project,
                                        ResUtils.getString("config_clear_jump_list_success"),
                                        5000L
                                    )
                                    hide()
                                }
                                throw ExecuteException(ResUtils.getString("config_clear_jump_list_failed"))
                            }
                        }

                        override fun onExecFailed(t: Throwable) {
                            Messages.showMessageDialog(
                                dialogContentPanel,
                                StringUtils.getErrorTip(t),
                                "CodeLocator",
                                Messages.getInformationIcon()
                            )
                        }
                    })
            }
        })
        dialogContentPanel.add(clearButton)
        dialogContentPanel.add(Box.createVerticalStrut(CoordinateUtils.DEFAULT_BORDER))
    }

    private fun addConfigButton() {
        val jButton =
            JButton("<html><body style='text-align:center;font-size:11px;'>" + ResUtils.getString("save") + "</body></html>")
        JComponentUtils.setSize(jButton, 100, 35)
        rootPane.defaultButton = jButton
        jButton.addActionListener {
            CodeLocatorUserConfig.updateConfig(config)
            codeLocatorWindow.codelocatorConfig = config
            hide()
        }
        val createHorizontalBox = Box.createHorizontalBox()
        createHorizontalBox.add(Box.createHorizontalGlue())
        createHorizontalBox.add(jButton)
        createHorizontalBox.add(Box.createHorizontalGlue())
        dialogContentPanel.add(Box.createVerticalGlue())
        dialogContentPanel.add(createHorizontalBox)
    }
}
