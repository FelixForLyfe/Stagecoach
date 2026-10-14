package net.feelix.stagecoach.neoforge;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.feelix.stagecoach.gametest.EventGameTests;
import net.feelix.stagecoach.gametest.PackGameTests;
import net.feelix.stagecoach.gametest.StagecoachGameTests;
import net.feelix.stagecoach.gametest.TravelGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = "stagecoach")
public final class NeoForgeGameTests {
    private NeoForgeGameTests() {}

    private static final String NAMESPACE = "stagecoach_gametest";
    private static final List<Class<?>> CLASSES = List.of(StagecoachGameTests.class, TravelGameTests.class, PackGameTests.class,
            EventGameTests.class);

    static {
        NeoForge.EVENT_BUS.addListener(EventGameTests::onPreStart);
        NeoForge.EVENT_BUS.addListener(EventGameTests::onList);
        NeoForge.EVENT_BUS.addListener(EventGameTests::onEnded);
        NeoForge.EVENT_BUS.addListener(EventGameTests::onDiscover);
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> each((id, method, test) -> helper.register(id, invoker(method))));
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(NAMESPACE, "default"));
        each((id, method, test) -> event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                new TestData<>(environment, Identifier.parse(test.structure()), test.maxTicks(), test.setupTicks(), test.required(),
                        test.rotation(), test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(), test.skyAccess(), test.padding()))));
    }

    @FunctionalInterface
    private interface Visitor {
        void visit(Identifier id, Method method, GameTest test);
    }

    private static void each(Visitor visitor) {
        for (Class<?> cls : CLASSES) {
            for (Method method : cls.getDeclaredMethods()) {
                GameTest test = method.getAnnotation(GameTest.class);
                if (test != null) visitor.visit(Identifier.fromNamespaceAndPath(NAMESPACE, snake(cls.getSimpleName()) + "_" + snake(method.getName())), method, test);
            }
        }
    }

    /** "TravelGameTests" -> "travel_game_tests", as Fabric names them. */
    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private static Consumer<GameTestHelper> invoker(Method method) {
        return helper -> {
            try {
                Object target = Modifier.isStatic(method.getModifiers()) ? null : method.getDeclaringClass().getDeclaredConstructor().newInstance();
                method.invoke(target, helper);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                throw new RuntimeException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
