package com.frenkvs.devmod.client.screen;

import com.frenkvs.devmod.network.payload.EquipMobPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MobEquipmentScreen extends Screen {

    private final Mob mob;
    private final Screen parentScreen; // Serve per tornare indietro quando premi "Indietro"

    private EditBox mainHand, offHand, head, chest, legs, feet;

    public MobEquipmentScreen(Mob mob, Screen parentScreen) {
        super(Component.literal("Equipaggiamento: " + mob.getName().getString()));
        this.mob = mob;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int w = 120; // Larghezza caselle
        int h = 20;
        int centerX = width / 2;
        int y = 40;

        // -- MANO DESTRA --
        addLabel(centerX - 130, y, "Mano Destra:");
        mainHand = new EditBox(font, centerX - 50, y, w, h, Component.literal("Main"));
        mainHand.setValue(getItemName(EquipmentSlot.MAINHAND));
        this.addRenderableWidget(mainHand);

        // -- MANO SINISTRA --
        y += 25;
        addLabel(centerX - 130, y, "Mano Sinistra:");
        offHand = new EditBox(font, centerX - 50, y, w, h, Component.literal("Off"));
        offHand.setValue(getItemName(EquipmentSlot.OFFHAND));
        this.addRenderableWidget(offHand);

        // -- ELMO --
        y += 35; // Spazio extra
        addLabel(centerX - 130, y, "Testa (Elmo):");
        head = new EditBox(font, centerX - 50, y, w, h, Component.literal("Head"));
        head.setValue(getItemName(EquipmentSlot.HEAD));
        this.addRenderableWidget(head);

        // -- PETTORALE --
        y += 25;
        addLabel(centerX - 130, y, "Corpo (Chest):");
        chest = new EditBox(font, centerX - 50, y, w, h, Component.literal("Chest"));
        chest.setValue(getItemName(EquipmentSlot.CHEST));
        this.addRenderableWidget(chest);

        // -- PANTALONI --
        y += 25;
        addLabel(centerX - 130, y, "Gambe (Legs):");
        legs = new EditBox(font, centerX - 50, y, w, h, Component.literal("Legs"));
        legs.setValue(getItemName(EquipmentSlot.LEGS));
        this.addRenderableWidget(legs);

        // -- STIVALI --
        y += 25;
        addLabel(centerX - 130, y, "Piedi (Boots):");
        feet = new EditBox(font, centerX - 50, y, w, h, Component.literal("Feet"));
        feet.setValue(getItemName(EquipmentSlot.FEET));
        this.addRenderableWidget(feet);

        // -- BOTTONI --
        y += 35;

        // Bottone APPLICA
        this.addRenderableWidget(Button.builder(Component.literal("APPLICA"), b -> save())
                .pos(centerX - 105, y).size(100, 20).build());

        // Bottone INDIETRO
        this.addRenderableWidget(Button.builder(Component.literal("INDIETRO"), b -> onClose())
                .pos(centerX + 5, y).size(100, 20).build());
    }

    private String getItemName(EquipmentSlot slot) {
        ItemStack stack = mob.getItemBySlot(slot);
        if (stack.isEmpty()) return "";
        // Ritorna l'ID (es. minecraft:diamond_sword)
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // Metodo helper per le etichette
    private void addLabel(int x, int y, String text) {
        Button label = Button.builder(Component.literal(text), b -> {}).pos(x, y).size(75, 20).build();
        label.active = false;
        this.addRenderableWidget(label);
    }

    private void save() {
        // Invia i dati al server
        PacketDistributor.sendToServer(new EquipMobPayload(
                mob.getId(),
                mainHand.getValue(),
                offHand.getValue(),
                head.getValue(),
                chest.getValue(),
                legs.getValue(),
                feet.getValue()
        ));
    }

    @Override
    public void onClose() {
        // Quando chiudiamo, torniamo alla schermata principale dei mob
        net.minecraft.client.Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, this.title, width / 2, 10, 0xFFFFFF);
    }
}