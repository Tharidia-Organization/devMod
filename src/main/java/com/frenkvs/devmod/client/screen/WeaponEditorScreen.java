package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.manager.WeaponConfigManager;
import com.frenkvs.devmod.config.WeaponStats;
import com.frenkvs.devmod.network.payload.UpdateWeaponPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class WeaponEditorScreen extends Screen {

    private final ItemStack stack;
    private boolean editGlobal = false; // false = Modifica SOLO questa spada, true = TUTTE

    private EditBox penField, bonusField, nameField;

    public WeaponEditorScreen() {
        super(Component.literal("Editor Armi"));
        // Otteniamo l'oggetto nella mano principale
        this.stack = Minecraft.getInstance().player.getMainHandItem();
    }

    @Override
    protected void init() {
        int w = 80; int h = 20;
        int x = width / 2 - 100;
        int y = 40;

        // Toggle Global/Specific
        this.addRenderableWidget(Button.builder(
                        Component.literal("Modo: " + (editGlobal ? "GLOBALE (Tutte)" : "SPECIFICO (Questa)")),
                        b -> {
                            editGlobal = !editGlobal;
                            b.setMessage(Component.literal("Modo: " + (editGlobal ? "GLOBALE (Tutte)" : "SPECIFICO (Questa)")));
                            loadValues(); // Ricarica i valori quando cambi modalità
                        })
                .pos(x, 10).size(200, h).build());

        // Campi di testo
        WeaponStats current = WeaponConfigManager.getStats(stack);

        y += 25;
        addLabel(x, y, "Armor Pen:");
        penField = new EditBox(font, x + 100, y, w, h, Component.literal("Pen"));
        this.addRenderableWidget(penField);

        y += 25;
        addLabel(x, y, "Bonus Dmg:");
        bonusField = new EditBox(font, x + 100, y, w, h, Component.literal("Bonus"));
        this.addRenderableWidget(bonusField);

        y += 30;
        addLabel(x, y, "Nuovo Nome:");
        nameField = new EditBox(font, x + 100, y, w, h, Component.literal("Nome"));
        // Se stiamo modificando una specifica arma, mostriamo il suo nome attuale
        if (!editGlobal) nameField.setValue(stack.getHoverName().getString());
        this.addRenderableWidget(nameField);

        loadValues(); // Carica i dati iniziali nelle caselle

        // Bottone SALVA
        this.addRenderableWidget(Button.builder(Component.literal("SALVA MODIFICHE"), b -> save())
                .pos(width / 2 - 60, y + 40).size(120, h).build());
    }

    // CORREZIONE QUI: Usiamo un bottone disattivato come etichetta
    // Questo risolve l'errore "not a statement"
    private void addLabel(int x, int y, String text) {
        Button label = Button.builder(Component.literal(text), b -> {})
                .pos(x, y)
                .size(90, 20) // Larghezza, Altezza
                .build();
        label.active = false; // Lo disattiviamo così sembra un testo fisso
        this.addRenderableWidget(label);
    }

    private void loadValues() {
        // Recuperiamo le statistiche attuali dell'arma
        WeaponStats stats = WeaponConfigManager.getStats(stack);

        penField.setValue(String.valueOf(stats.armorPenetration));
        bonusField.setValue(String.valueOf(stats.baseDamageBonus));
    }

    private void save() {
        try {
            float pen = Float.parseFloat(penField.getValue());
            float bonus = Float.parseFloat(bonusField.getValue());
            String name = nameField.getValue();

            // Inviamo il pacchetto al server
            PacketDistributor.sendToServer(new UpdateWeaponPayload(editGlobal, pen, bonus, name));

            this.onClose(); // Chiudiamo la finestra
        } catch (Exception e) {
            // Se scrivi lettere invece di numeri, ignoriamo
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Titolo in alto
        guiGraphics.drawCenteredString(font, this.title, width / 2, 5, 0xFFFFFF);

        // Disegna l'icona dell'arma al centro, sopra i bottoni
        guiGraphics.renderItem(stack, width / 2 - 8, 25);
    }
}