package net.kuina.nebulaecraft.gui;

import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.autogen.template.AutogenParameter;
import net.kuina.nebulaecraft.network.PacketAutogenAction;
import net.kuina.nebulaecraft.network.PacketAutogenGui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Non-container configuration screen opened by the automatic generation wand. */
public final class GuiAutogen extends GuiScreen {
    private static final int TEMPLATE_BUTTON = 10;
    private static final int PARAMETER_BUTTON_BASE = 100;
    private static final int PREVIEW_BUTTON = 20;
    private static final int CONFIRM_BUTTON = 21;
    private static final int CANCEL_BUTTON = 22;
    private static final int UNDO_BUTTON = 23;
    private static final int CLEAR_BUTTON = 24;
    private static final int REFRESH_BUTTON = 25;
    private static final int CLOSE_BUTTON = 26;

    private static final int TEMPLATE_DROPDOWN_WIDTH = 260;
    private static final int TEMPLATE_DROPDOWN_ROW_HEIGHT = 20;
    private static final int TEMPLATE_DROPDOWN_MAX_ROWS = 6;
    private static final int TEMPLATE_DROPDOWN_SCROLLBAR_WIDTH = 6;

    private final PacketAutogenGui state;
    private int templateIndex;
    private boolean templateDropdownOpen;
    private int templateDropdownScroll;
    private List<String> values = new ArrayList<>();

    public GuiAutogen(PacketAutogenGui state) {
        this.state = state;
        selectTemplate(0);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = width / 2 - 145;
        int top = height / 2 - 105;

        GuiButton template = new GuiButton(TEMPLATE_BUTTON, left + 15, top + 49,
                TEMPLATE_DROPDOWN_WIDTH, TEMPLATE_DROPDOWN_ROW_HEIGHT,
                templateText());
        template.enabled = state.templates.size() > 1;
        buttonList.add(template);

        PacketAutogenGui.TemplateData selected = selectedTemplate();
        if (selected != null) {
            for (int i = 0; i < selected.parameters.size(); i++) {
                buttonList.add(new GuiButton(PARAMETER_BUTTON_BASE + i,
                        left + 15, top + 73 + i * 22, 260, 20,
                        parameterText(i)));
            }
        }

        int actionY = top + 145;
        GuiButton preview = new GuiButton(PREVIEW_BUTTON, left + 15, actionY, 82, 20,
                I18n.format("gui.nebulaecraft.autogen.preview"));
        preview.enabled = state.hasStart && state.hasEnd && !state.active && selected != null;
        buttonList.add(preview);

        GuiButton confirm = new GuiButton(CONFIRM_BUTTON, left + 104, actionY, 82, 20,
                I18n.format("gui.nebulaecraft.autogen.confirm"));
        confirm.enabled = state.hasPreview && !state.active;
        buttonList.add(confirm);

        GuiButton cancel = new GuiButton(CANCEL_BUTTON, left + 193, actionY, 82, 20,
                I18n.format("gui.nebulaecraft.autogen.cancel"));
        cancel.enabled = state.hasPreview || state.canCancelActive;
        buttonList.add(cancel);

        int secondY = actionY + 24;
        GuiButton undo = new GuiButton(UNDO_BUTTON, left + 15, secondY, 82, 20,
                I18n.format("gui.nebulaecraft.autogen.undo"));
        undo.enabled = state.canUndo;
        buttonList.add(undo);

        GuiButton clear = new GuiButton(CLEAR_BUTTON, left + 104, secondY, 82, 20,
                I18n.format("gui.nebulaecraft.autogen.clear"));
        clear.enabled = state.hasStart || state.hasEnd;
        buttonList.add(clear);

        buttonList.add(new GuiButton(REFRESH_BUTTON, left + 193, secondY, 82, 20,
                I18n.format("gui.nebulaecraft.autogen.refresh")));

        buttonList.add(new GuiButton(CLOSE_BUTTON, left + 15, secondY + 24, 260, 20,
                I18n.format("gui.nebulaecraft.autogen.close")));

        clampTemplateDropdownScroll();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == TEMPLATE_BUTTON && !state.templates.isEmpty()) {
            templateDropdownOpen = !templateDropdownOpen;
            if (templateDropdownOpen) {
                scrollSelectedTemplateIntoView();
            }
            return;
        }
        if (button.id >= PARAMETER_BUTTON_BASE) {
            int index = button.id - PARAMETER_BUTTON_BASE;
            cycleParameter(index);
            button.displayString = parameterText(index);
            return;
        }

        switch (button.id) {
            case PREVIEW_BUTTON:
                PacketAutogenGui.TemplateData template = selectedTemplate();
                if (template != null) {
                    send(new PacketAutogenAction(PacketAutogenAction.PREVIEW,
                            template.id, commandArguments(template)));
                    mc.displayGuiScreen(null);
                }
                break;
            case CONFIRM_BUTTON:
                sendAndClose(PacketAutogenAction.CONFIRM);
                break;
            case CANCEL_BUTTON:
                sendAndClose(PacketAutogenAction.CANCEL);
                break;
            case UNDO_BUTTON:
                sendAndClose(PacketAutogenAction.UNDO);
                break;
            case CLEAR_BUTTON:
                send(new PacketAutogenAction(PacketAutogenAction.CLEAR));
                break;
            case REFRESH_BUTTON:
                send(new PacketAutogenAction(PacketAutogenAction.REFRESH));
                break;
            case CLOSE_BUTTON:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int top = height / 2 - 105;
        drawCenteredString(fontRenderer, I18n.format("gui.nebulaecraft.autogen.title"),
                width / 2, top + 7, 0xFFFFFF);
        drawCenteredString(fontRenderer,
                I18n.format("gui.nebulaecraft.autogen.start") + ": "
                        + (state.hasStart ? state.start : I18n.format("gui.nebulaecraft.autogen.not_selected")),
                width / 2, top + 23, state.hasStart ? 0xA0FFA0 : 0xFF9090);
        drawCenteredString(fontRenderer,
                I18n.format("gui.nebulaecraft.autogen.end") + ": "
                        + (state.hasEnd ? state.end : I18n.format("gui.nebulaecraft.autogen.not_selected")),
                width / 2, top + 34, state.hasEnd ? 0xA0FFA0 : 0xFF9090);
        drawCenteredString(fontRenderer,
                I18n.format("gui.nebulaecraft.autogen.status") + ": " + state.status,
                width / 2, top + 132, 0xD0D0D0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (templateDropdownOpen) {
            drawTemplateDropdown(mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!templateDropdownOpen) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 0 && isInsideTemplateButton(mouseX, mouseY)) {
            templateDropdownOpen = false;
            return;
        }

        if (mouseButton == 0 && isInsideTemplateDropdown(mouseX, mouseY)) {
            int visibleRows = getVisibleTemplateRows();
            int dropdownX = getTemplateDropdownX();
            int dropdownY = getTemplateDropdownY();
            if (state.templates.size() > visibleRows
                    && mouseX >= dropdownX + TEMPLATE_DROPDOWN_WIDTH
                    - TEMPLATE_DROPDOWN_SCROLLBAR_WIDTH) {
                int maximumScroll = state.templates.size() - visibleRows;
                int dropdownHeight = visibleRows * TEMPLATE_DROPDOWN_ROW_HEIGHT;
                templateDropdownScroll = clamp((mouseY - dropdownY) * maximumScroll
                        / Math.max(1, dropdownHeight - 1), 0, maximumScroll);
                return;
            }

            int index = templateDropdownScroll
                    + (mouseY - dropdownY) / TEMPLATE_DROPDOWN_ROW_HEIGHT;
            if (index >= 0 && index < state.templates.size()) {
                if (index != templateIndex) {
                    selectTemplate(index);
                    initGui();
                }
                templateDropdownOpen = false;
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord
                        .getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return;
        }

        // Covered controls are handled above; clicks elsewhere may activate visible controls
        // such as the dedicated close button in the same click.
        templateDropdownOpen = false;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (templateDropdownOpen && wheel != 0) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (isInsideTemplateButton(mouseX, mouseY)
                    || isInsideTemplateDropdown(mouseX, mouseY)) {
                templateDropdownScroll += wheel > 0 ? -1 : 1;
                clampTemplateDropdownScroll();
                return;
            }
        }
        super.handleMouseInput();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (templateDropdownOpen && keyCode == Keyboard.KEY_ESCAPE) {
            templateDropdownOpen = false;
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void selectTemplate(int index) {
        templateIndex = state.templates.isEmpty() ? -1 : index;
        values = new ArrayList<>();
        PacketAutogenGui.TemplateData template = selectedTemplate();
        if (template != null) {
            for (PacketAutogenGui.ParameterData parameter : template.parameters) {
                values.add(parameter.defaultValue);
            }
        }
    }

    private PacketAutogenGui.TemplateData selectedTemplate() {
        return templateIndex < 0 || templateIndex >= state.templates.size()
                ? null : state.templates.get(templateIndex);
    }

    private String templateText() {
        PacketAutogenGui.TemplateData template = selectedTemplate();
        return I18n.format("gui.nebulaecraft.autogen.template") + ": "
                + (template == null ? "-" : template.displayName)
                + (state.templates.size() > 1 ? "  \u25BC" : "");
    }

    private void drawTemplateDropdown(int mouseX, int mouseY) {
        int visibleRows = getVisibleTemplateRows();
        if (visibleRows <= 0) {
            return;
        }

        int x = getTemplateDropdownX();
        int y = getTemplateDropdownY();
        int height = visibleRows * TEMPLATE_DROPDOWN_ROW_HEIGHT;
        boolean scrollable = state.templates.size() > visibleRows;
        int textWidth = TEMPLATE_DROPDOWN_WIDTH - (scrollable
                ? TEMPLATE_DROPDOWN_SCROLLBAR_WIDTH : 0) - 8;

        drawRect(x - 1, y - 1, x + TEMPLATE_DROPDOWN_WIDTH + 1, y + height + 1,
                0xFF000000);
        for (int row = 0; row < visibleRows; row++) {
            int index = templateDropdownScroll + row;
            int rowY = y + row * TEMPLATE_DROPDOWN_ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + TEMPLATE_DROPDOWN_WIDTH
                    - (scrollable ? TEMPLATE_DROPDOWN_SCROLLBAR_WIDTH : 0)
                    && mouseY >= rowY && mouseY < rowY + TEMPLATE_DROPDOWN_ROW_HEIGHT;
            int background = hovered ? 0xFF4A6D8C
                    : index == templateIndex ? 0xFF355A7A : 0xFF303030;
            drawRect(x, rowY, x + TEMPLATE_DROPDOWN_WIDTH, rowY
                    + TEMPLATE_DROPDOWN_ROW_HEIGHT, background);
            if (row > 0) {
                drawHorizontalLine(x, x + TEMPLATE_DROPDOWN_WIDTH - 1, rowY,
                        0xFF555555);
            }

            String label = fontRenderer.trimStringToWidth(
                    state.templates.get(index).displayName, textWidth);
            drawCenteredString(fontRenderer, label,
                    x + (TEMPLATE_DROPDOWN_WIDTH
                            - (scrollable ? TEMPLATE_DROPDOWN_SCROLLBAR_WIDTH : 0)) / 2,
                    rowY + 6, hovered ? 0xFFFFA0 : 0xFFFFFF);
        }

        if (scrollable) {
            int scrollbarX = x + TEMPLATE_DROPDOWN_WIDTH - TEMPLATE_DROPDOWN_SCROLLBAR_WIDTH;
            drawRect(scrollbarX, y, x + TEMPLATE_DROPDOWN_WIDTH, y + height, 0xFF181818);
            int thumbHeight = Math.max(8, height * visibleRows / state.templates.size());
            int maximumScroll = state.templates.size() - visibleRows;
            int thumbY = y + (height - thumbHeight) * templateDropdownScroll / maximumScroll;
            drawRect(scrollbarX + 1, thumbY, x + TEMPLATE_DROPDOWN_WIDTH - 1,
                    thumbY + thumbHeight, 0xFFB0B0B0);
        }
    }

    private int getTemplateDropdownX() {
        return width / 2 - 130;
    }

    private int getTemplateButtonY() {
        return height / 2 - 56;
    }

    private int getTemplateDropdownY() {
        return getTemplateButtonY() + TEMPLATE_DROPDOWN_ROW_HEIGHT;
    }

    private int getVisibleTemplateRows() {
        int rowsThatFit = Math.max(1,
                (height - getTemplateDropdownY() - 4) / TEMPLATE_DROPDOWN_ROW_HEIGHT);
        return Math.min(state.templates.size(),
                Math.min(TEMPLATE_DROPDOWN_MAX_ROWS, rowsThatFit));
    }

    private boolean isInsideTemplateButton(int mouseX, int mouseY) {
        int x = getTemplateDropdownX();
        int y = getTemplateButtonY();
        return mouseX >= x && mouseX < x + TEMPLATE_DROPDOWN_WIDTH
                && mouseY >= y && mouseY < y + TEMPLATE_DROPDOWN_ROW_HEIGHT;
    }

    private boolean isInsideTemplateDropdown(int mouseX, int mouseY) {
        int visibleRows = getVisibleTemplateRows();
        int x = getTemplateDropdownX();
        int y = getTemplateDropdownY();
        return mouseX >= x && mouseX < x + TEMPLATE_DROPDOWN_WIDTH
                && mouseY >= y && mouseY < y
                + visibleRows * TEMPLATE_DROPDOWN_ROW_HEIGHT;
    }

    private void scrollSelectedTemplateIntoView() {
        int visibleRows = getVisibleTemplateRows();
        if (templateIndex < templateDropdownScroll) {
            templateDropdownScroll = templateIndex;
        } else if (templateIndex >= templateDropdownScroll + visibleRows) {
            templateDropdownScroll = templateIndex - visibleRows + 1;
        }
        clampTemplateDropdownScroll();
    }

    private void clampTemplateDropdownScroll() {
        templateDropdownScroll = clamp(templateDropdownScroll, 0,
                Math.max(0, state.templates.size() - getVisibleTemplateRows()));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void cycleParameter(int index) {
        PacketAutogenGui.TemplateData template = selectedTemplate();
        if (template == null || index < 0 || index >= template.parameters.size()) {
            return;
        }
        PacketAutogenGui.ParameterData parameter = template.parameters.get(index);
        if (parameter.type == AutogenParameter.Type.BOOLEAN) {
            values.set(index, Boolean.toString(!Boolean.parseBoolean(values.get(index))));
            return;
        }
        if (!parameter.choices.isEmpty()) {
            int current = parameter.choices.indexOf(values.get(index));
            values.set(index, parameter.choices.get((current + 1) % parameter.choices.size()));
        }
    }

    private String parameterText(int index) {
        PacketAutogenGui.TemplateData template = selectedTemplate();
        if (template == null || index < 0 || index >= template.parameters.size()) {
            return "-";
        }
        PacketAutogenGui.ParameterData parameter = template.parameters.get(index);
        String value = values.get(index);
        String displayValue;
        if (parameter.type == AutogenParameter.Type.BOOLEAN) {
            displayValue = I18n.format(Boolean.parseBoolean(value)
                    ? "gui.nebulaecraft.autogen.yes" : "gui.nebulaecraft.autogen.no");
        } else {
            String valueKey = "gui.nebulaecraft.autogen.value." + value;
            displayValue = I18n.hasKey(valueKey) ? I18n.format(valueKey) : value;
        }
        return I18n.format(parameter.labelKey) + ": " + displayValue;
    }

    private List<String> commandArguments(PacketAutogenGui.TemplateData template) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < template.parameters.size(); i++) {
            PacketAutogenGui.ParameterData parameter = template.parameters.get(i);
            String value = values.get(i);
            if (parameter.type == AutogenParameter.Type.BOOLEAN) {
                if (Boolean.parseBoolean(value)) {
                    result.add(parameter.trueArgument);
                }
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private void sendAndClose(int action) {
        send(new PacketAutogenAction(action));
        mc.displayGuiScreen(null);
    }

    private static void send(PacketAutogenAction packet) {
        NebulaecraftMod.PACKET_HANDLER.sendToServer(packet);
    }
}
