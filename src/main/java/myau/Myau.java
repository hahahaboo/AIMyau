package myau;

import myau.command.CommandManager;
import myau.command.commands.*;
import myau.config.Config;
import myau.config.HideConfig;
import myau.event.EventManager;
import myau.management.*;
import myau.module.Module;
import myau.module.ModuleManager;
import myau.module.modules.*;
import myau.property.Property;
import myau.property.PropertyManager;
import myau.ui.impl.notification.NotificationRenderer;
import myau.util.font.FontResourceManager;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class Myau {
    public static final NotificationRenderer notificationRenderer = new NotificationRenderer();
    public static String clientName = "§l§5[§6AIMyau§5]§r ";
    public static String clientVersion = "beta";
    public static BlinkManager blinkManager;
    public static CommandManager commandManager;
    public static Config globalConfig;
    public static DelayManager delayManager;
    public static FloatManager floatManager;
    public static FriendManager friendManager;
    public static HideConfig hideConfig;
    public static LagManager lagManager;
    public static ModuleManager moduleManager;
    public static PlayerStateManager playerStateManager;
    public static PropertyManager propertyManager;
    public static RotationManager rotationManager;
    public static TargetManager targetManager;

    public Myau() {
        this.init();
    }

    public void init() {
        blinkManager = new BlinkManager();
        commandManager = new CommandManager();
        delayManager = new DelayManager();
        floatManager = new FloatManager();
        friendManager = new FriendManager();
        lagManager = new LagManager();
        moduleManager = new ModuleManager();
        playerStateManager = new PlayerStateManager();
        propertyManager = new PropertyManager();
        rotationManager = new RotationManager();
        targetManager = new TargetManager();

        EventManager.register(blinkManager);
        EventManager.register(commandManager);
        EventManager.register(delayManager);
        EventManager.register(floatManager);
        EventManager.register(lagManager);
        EventManager.register(moduleManager);
        EventManager.register(notificationRenderer);
        EventManager.register(rotationManager);

        moduleManager.modules.put(AimAssist.class, new AimAssist());
        moduleManager.modules.put(Animations.class, new Animations());
        moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
        moduleManager.modules.put(AntiFireball.class, new AntiFireball());
        moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
        moduleManager.modules.put(AntiVoid.class, new AntiVoid());
        moduleManager.modules.put(AutoBlockIn.class, new AutoBlockIn());
        moduleManager.modules.put(AutoClicker.class, new AutoClicker());
        moduleManager.modules.put(AutoTool.class, new AutoTool());
        moduleManager.modules.put(AutoWeapon.class, new AutoWeapon());
        moduleManager.modules.put(BackTrack.class, new BackTrack());
        moduleManager.modules.put(BedESP.class, new BedESP());
        moduleManager.modules.put(BedNuker.class, new BedNuker());
        moduleManager.modules.put(Blink.class, new Blink());
        moduleManager.modules.put(Chams.class, new Chams());
        moduleManager.modules.put(ChestESP.class, new ChestESP());
        moduleManager.modules.put(ChestStealer.class, new ChestStealer());
        moduleManager.modules.put(ClickGUIModule.class, new ClickGUIModule());
        moduleManager.modules.put(Eagle.class, new Eagle());
        moduleManager.modules.put(ESP.class, new ESP());
        moduleManager.modules.put(FastPlace.class, new FastPlace());
        moduleManager.modules.put(Fly.class, new Fly());
        moduleManager.modules.put(FreeLook.class, new FreeLook());
        moduleManager.modules.put(FullBright.class, new FullBright());
        moduleManager.modules.put(GhostHand.class, new GhostHand());
        moduleManager.modules.put(HUD.class, new HUD());
        moduleManager.modules.put(Indicators.class, new Indicators());
        moduleManager.modules.put(InvManager.class, new InvManager());
        moduleManager.modules.put(InvWalk.class, new InvWalk());
        moduleManager.modules.put(ItemESP.class, new ItemESP());
        moduleManager.modules.put(Jesus.class, new Jesus());
        moduleManager.modules.put(JumpReset.class, new JumpReset());
        moduleManager.modules.put(KeepSprint.class, new KeepSprint());
        moduleManager.modules.put(KillAura.class, new KillAura());
        moduleManager.modules.put(LagRange.class, new LagRange());
        moduleManager.modules.put(LongJump.class, new LongJump());
        moduleManager.modules.put(MCF.class, new MCF());
        moduleManager.modules.put(MoreKB.class, new MoreKB());
        moduleManager.modules.put(NameTags.class, new NameTags());
        moduleManager.modules.put(NickHider.class, new NickHider());
        moduleManager.modules.put(NoFall.class, new NoFall());
        moduleManager.modules.put(NoHitDelay.class, new NoHitDelay());
        moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
        moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
        moduleManager.modules.put(NoRotate.class, new NoRotate());
        moduleManager.modules.put(NoSlow.class, new NoSlow());
        moduleManager.modules.put(NotificationModule.class, new NotificationModule());
        moduleManager.modules.put(Reach.class, new Reach());
        moduleManager.modules.put(SafeWalk.class, new SafeWalk());
        moduleManager.modules.put(Scaffold.class, new Scaffold());
        moduleManager.modules.put(Spammer.class, new Spammer());
        moduleManager.modules.put(Speed.class, new Speed());
        moduleManager.modules.put(SpeedMine.class, new SpeedMine());
        moduleManager.modules.put(Sprint.class, new Sprint());
        moduleManager.modules.put(TargetHUD.class, new TargetHUD());
        moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
        moduleManager.modules.put(Tracers.class, new Tracers());
        moduleManager.modules.put(Trajectories.class, new Trajectories());
        moduleManager.modules.put(Velocity.class, new Velocity());
        moduleManager.modules.put(ViewClip.class, new ViewClip());
        moduleManager.modules.put(WaterMark.class, new WaterMark());
        moduleManager.modules.put(Wtap.class, new Wtap());

        commandManager.commands.add(new BindCommand());
        commandManager.commands.add(new ClickGuiCommand());
        commandManager.commands.add(new ConfigCommand());
        commandManager.commands.add(new DenickCommand());
        commandManager.commands.add(new FriendCommand());
        commandManager.commands.add(new HelpCommand());
        commandManager.commands.add(new IgnCommand());
        commandManager.commands.add(new ItemCommand());
        commandManager.commands.add(new ListCommand());
        commandManager.commands.add(new ModuleCommand());
        commandManager.commands.add(new PlayerCommand());
        commandManager.commands.add(new TargetCommand());
        commandManager.commands.add(new ToggleCommand());
        commandManager.commands.add(new VclipCommand());

        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();
            for (final Field field : module.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                final Object obj;
                try {
                    obj = field.get(module);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                if (obj instanceof Property<?>) {
                    ((Property<?>) obj).setOwner(module);
                    properties.add((Property<?>) obj);
                }
            }
            propertyManager.properties.put(module.getClass(), properties);
            EventManager.register(module);
        }
        globalConfig = new Config("default", true);
        hideConfig = new HideConfig("Hide", true);
        if (globalConfig.file.exists()) {
            globalConfig.load();
        }
        if (hideConfig.file.exists()) {
            hideConfig.load();
        }
        if (friendManager.file.exists()) {
            friendManager.load();
        }
        if (targetManager.file.exists()) {
            targetManager.load();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            FontResourceManager.cleanupAllFonts();
            globalConfig.save();
            hideConfig.save();
        }));
    }
}
