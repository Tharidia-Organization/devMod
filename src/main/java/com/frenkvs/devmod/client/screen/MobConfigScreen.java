package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.network.payload.UpdateMobStatsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

public class MobConfigScreen extends Screen {
    private final Mob mob;

    // Caselle di testo
    private EditBox rangeField;
    private EditBox damageField;
    private EditBox healthField;
    private EditBox armorField;
    private EditBox attackRangeField;

    // Modalità Globale vs Specifica
    private boolean isGlobalMode = false;
    private Button modeButton;

    public MobConfigScreen(Mob mob) {
        super(Component.literal("Configura: " + mob.getName().getString()));
        this.mob = mob;
    }

    @Override
    protected void init() {
        int w = 80; // Larghezza caselle
        int h = 20;
        int xCenter = width / 2;
        int startY = height / 2 - 110; // Posizione verticale iniziale

        // --- 1. BOTTONE MODALITÀ (Globale / Specifico) ---
        modeButton = Button.builder(Component.literal("MODO: SPECIFICO (Solo questo)"), b -> {
                    isGlobalMode = !isGlobalMode;
                    updateModeButtonText();
                })
                .pos(xCenter - 100, startY - 25).size(200, 20).build();
        this.addRenderableWidget(modeButton);

        // --- 2. CASELLE STATISTICHE ---

        // HP
        addLabel(xCenter - 100, startY, "Max HP:");
        healthField = new EditBox(font, xCenter - 40, startY, w, h, Component.literal("HP"));
        healthField.setValue(String.valueOf(getVal(Attributes.MAX_HEALTH)));
        this.addRenderableWidget(healthField);

        // Armor
        int y2 = startY + 25;
        addLabel(xCenter - 100, y2, "Armor:");
        armorField = new EditBox(font, xCenter - 40, y2, w, h, Component.literal("Armor"));
        armorField.setValue(String.valueOf(getVal(Attributes.ARMOR)));
        this.addRenderableWidget(armorField);

        // Damage
        int y3 = y2 + 25;
        addLabel(xCenter - 100, y3, "Damage:");
        damageField = new EditBox(font, xCenter - 40, y3, w, h, Component.literal("Damage"));
        damageField.setValue(String.valueOf(getVal(Attributes.ATTACK_DAMAGE)));
        this.addRenderableWidget(damageField);

        // Follow Range (Vista)
        int y4 = y3 + 25;
        addLabel(xCenter - 100, y4, "View Dist:");
        rangeField = new EditBox(font, xCenter - 40, y4, w, h, Component.literal("Range"));
        rangeField.setValue(String.valueOf(getVal(Attributes.FOLLOW_RANGE)));
        this.addRenderableWidget(rangeField);

        // Attack Reach (Usa LUCK come trucco)
        int y5 = y4 + 25;
        addLabel(xCenter - 100, y5, "Atk Reach:");
        attackRangeField = new EditBox(font, xCenter - 40, y5, w, h, Component.literal("Reach"));

        // Recupero intelligente del reach
        double currentReach = getVal(Attributes.LUCK);
        if (currentReach <= 0.1) {
            currentReach = mob.getBbWidth() * 2.0 + 1.0;
        }
        attackRangeField.setValue(String.valueOf(currentReach));
        this.addRenderableWidget(attackRangeField);

        // --- 3. BOTTONI AZIONE ---
        int yButtons = y5 + 35;

        // Bottone APPLICA
        this.addRenderableWidget(Button.builder(Component.literal("APPLICA"), button -> save())
                .pos(xCenter - 105, yButtons)
                .size(100, 20)
                .build());

        // Bottone EQUIPAGGIAMENTO
        this.addRenderableWidget(Button.builder(Component.literal("EQUIPAGGIAMENTO..."), button -> {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new MobEquipmentScreen(mob, this));
                })
                .pos(xCenter + 5, yButtons)
                .size(100, 20)
                .build());
    }

    private void updateModeButtonText() {
        if (isGlobalMode) {
            modeButton.setMessage(Component.literal("§6MODO: GLOBALE (Tutti i futuri)"));
        } else {
            modeButton.setMessage(Component.literal("§fMODO: SPECIFICO (Solo questo)"));
        }
    }

    // Metodo per creare etichette (usa bottoni disattivati per evitare errori di render)
    private void addLabel(int x, int y, String text) {
        Button label = Button.builder(Component.literal(text), b -> {}).pos(x, y).size(55, 20).build();
        label.active = false;
        this.addRenderableWidget(label);
    }

    private double getVal(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        if (mob.getAttribute(attr) != null) return mob.getAttributeValue(attr);
        return 0;
    }

    private void save() {
        try {
            double range = Double.parseDouble(rangeField.getValue());
            double damage = Double.parseDouble(damageField.getValue());
            double hp = Double.parseDouble(healthField.getValue());
            double armor = Double.parseDouble(armorField.getValue());
            double atkReach = Double.parseDouble(attackRangeField.getValue());

            // Inviamo il pacchetto con il flag 'isGlobalMode'
            PacketDistributor.sendToServer(new UpdateMobStatsPayload(isGlobalMode, mob.getId(), range, damage, hp, armor, atkReach));

            this.onClose();
        } catch (NumberFormatException e) {
            // Ignora se non sono numeri
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 10, 0xFFFFFF);
    }
}