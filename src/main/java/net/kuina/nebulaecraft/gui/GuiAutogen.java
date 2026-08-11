package net.kuina.nebulaecraft.gui;

import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.autogen.template.AutogenParameter;
import net.kuina.nebulaecraft.network.PacketAutogenAction;
import net.kuina.nebulaecraft.network.PacketAutogenGui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

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

    private final PacketAutogenGui state;
    private int templateIndex;
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

        GuiButton template = new GuiButton(TEMPLATE_BUTTON, left + 15, top + 49, 260, 20,
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
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == TEMPLATE_BUTTON && !state.templates.isEmpty()) {
            selectTemplate((templateIndex + 1) % state.templates.size());
            initGui();
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
                + (template == null ? "-" : template.displayName);
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
