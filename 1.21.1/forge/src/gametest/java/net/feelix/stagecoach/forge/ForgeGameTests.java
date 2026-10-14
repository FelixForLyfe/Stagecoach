package net.feelix.stagecoach.forge;

import net.feelix.stagecoach.gametest.EventGameTests;
import net.feelix.stagecoach.gametest.PackGameTests;
import net.feelix.stagecoach.gametest.StagecoachGameTests;
import net.feelix.stagecoach.gametest.TravelGameTests;
import net.minecraft.test.GameTest;
import net.minecraft.test.StructureTestUtil;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestFunction;
import net.minecraft.test.TestFunctions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = "stagecoach", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeGameTests {
    private ForgeGameTests() {}

    private static final Logger LOG = LoggerFactory.getLogger("stagecoach-gametest");

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        int n = 0;
        for (Class<?> cls : new Class<?>[] {StagecoachGameTests.class, TravelGameTests.class, PackGameTests.class, EventGameTests.class}) {
            n += add(cls);
        }
        MinecraftForge.EVENT_BUS.addListener(EventGameTests::onPreStart);
        MinecraftForge.EVENT_BUS.addListener(EventGameTests::onList);
        MinecraftForge.EVENT_BUS.addListener(EventGameTests::onEnded);
        MinecraftForge.EVENT_BUS.addListener(EventGameTests::onDiscover);
        LOG.info("[stagecoach] registered {} GameTests", n);
    }

    @SuppressWarnings("unchecked")
    private static int add(Class<?> cls) {
        try {
            Field functions = TestFunctions.class.getDeclaredField("TEST_FUNCTIONS");
            Field classes = TestFunctions.class.getDeclaredField("TEST_CLASSES");
            functions.setAccessible(true);
            classes.setAccessible(true);
            Collection<TestFunction> all = (Collection<TestFunction>) functions.get(null);
            ((Set<String>) classes.get(null)).add(cls.getSimpleName());
            int n = 0;
            for (Method method : cls.getDeclaredMethods()) {
                GameTest test = method.getAnnotation(GameTest.class);
                if (test == null) continue;
                String name = cls.getSimpleName().toLowerCase() + "." + method.getName().toLowerCase();
                all.add(new TestFunction(test.batchId(), name, test.templateName().isEmpty() ? name : test.templateName(),
                        StructureTestUtil.getRotation(test.rotation()), test.tickLimit(), test.duration(), test.required(),
                        test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(), test.skyAccess(), invoker(method)));
                n++;
            }
            return n;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not register " + cls.getSimpleName(), e);
        }
    }

    private static Consumer<TestContext> invoker(Method method) {
        return ctx -> {
            try {
                Object target = Modifier.isStatic(method.getModifiers()) ? null
                        : method.getDeclaringClass().getDeclaredConstructor().newInstance();
                method.invoke(target, ctx);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                throw new RuntimeException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
