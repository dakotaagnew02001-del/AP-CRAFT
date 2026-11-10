package com.apcraft.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Bedrock-inspired recipe-book style categories; sends existing server-side commands. */
public class MechanicMenuScreen extends Screen {
    private static final String[] CATEGORIES = {"Jobs", "FAA Academy", "Practicals", "Study", "Tool Truck", "Aircraft", "Groceries", "Finances"};
    private static final String[][] LABELS = {
        {"Browse Jobs", "Continue Job", "Mechanic Profile", "Training Work Orders"},
        {"FAA Lesson Catalog", "General 1", "General 2", "General 3", "Airframe 5", "Airframe 6", "Powerplant 8", "Powerplant 9", "Source References"},
        {"Practical Stations", "Safety Wiring", "Multimeter", "Weight & Balance", "Magneto Timing", "Practical Progress"},
        {"Engine Run-Up", "100-Hour Inspection", "Generators", "Turbines", "Helicopters", "Other Work Orders", "Choose 1", "Choose 2", "Choose 3"},
        {"New FAA Question", "Review Missed", "Study Progress", "Answer 1", "Answer 2", "Answer 3", "Answer 4"},
        {"Pittsburgh", "ICON", "Mac Tools", "Snap-on", "Pittsburgh Box", "ICON Box", "Mac Box", "Snap-on Box"},
        {"Vehicle Showroom", "Cessna 172", "Piper Apache", "Cessna 310", "AH-64 Apache", "Fleet Showroom", "Start Flight", "Throttle 1", "Throttle 3", "Throttle 5", "Land"},
        {"Bread x8 - $12", "Apples x8 - $15", "Cooked Beef x8 - $45", "Iron x4 - $60"},
        {"Balance", "Bills", "Pay Debt", "Job Status"}
    };
    private static final String[][] COMMANDS = {
        {"jobboard open", "work status", "career", "training list"},
        {"faa lessons", "faa start 1", "faa start 2", "faa start 3", "faa start 5", "faa start 6", "faa start 8", "faa start 9", "faa source"},
        {"practical list", "practical start 1", "practical start 2", "practical start 3", "practical start 4", "practical status"},
        {"training start 1", "training start 2", "training start 3", "training start 4", "training start 5", "training list", "training choose 1", "training choose 2", "training choose 3"},
        {"apcraft quiz", "apcraft review", "apcraft stats", "apcraft answer 1", "apcraft answer 2", "apcraft answer 3", "apcraft answer 4"},
        {"tooltruck catalog 1", "tooltruck catalog 2", "tooltruck catalog 3", "tooltruck catalog 4", "tooltruck box 1", "tooltruck box 2", "tooltruck box 3", "tooltruck box 4"},
        {"fleet shop", "fleet buy 5", "fleet buy 6", "fleet buy 7", "fleet buy 8", "fleet shop", "fleet fly", "fleet throttle 1", "fleet throttle 3", "fleet throttle 5", "fleet land"},
        {"economy buy bread", "economy buy apples", "economy buy beef", "economy buy iron"},
        {"economy balance", "economy bills", "economy paydebt", "training status"}
    };
    private int category = 0;
    public MechanicMenuScreen() { super(Text.literal("A&P Craft - Mechanic Tablet")); }
    @Override protected void init() { build(); }
    private void build() {
        clearChildren();
        int x = (width - 500) / 2, y = (height - 360) / 2;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int index = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(CATEGORIES[i]), b -> {category=index;build();})
                .dimensions(x+8, y+35+i*35, 125, 27).build());
        }
        for (int i=0; i<LABELS[category].length; i++) {
            final String command = COMMANDS[category][i];
            int bx=x+143+(i%3)*115, by=y+35+(i/3)*54;
            addDrawableChild(ButtonWidget.builder(Text.literal(LABELS[category][i]), b -> execute(command))
                .dimensions(bx,by,109,44).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close()).dimensions(x+405,y+327,80,22).build());
    }
    private void execute(String command) {
        if (client != null && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand(command);
            close();
        }
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context,mouseX,mouseY,delta);
        int x=(width-500)/2,y=(height-360)/2;
        context.fill(x,y,x+500,y+360,0xEE22252A);
        context.fill(x+4,y+28,x+137,y+320,0xFF333840);
        context.drawTextWithShadow(textRenderer,"A&P CRAFT | MECHANIC TABLET",x+12,y+10,0xFFFFFFFF);
        super.render(context,mouseX,mouseY,delta);
    }
    @Override public boolean shouldPause() {return false;}
}
