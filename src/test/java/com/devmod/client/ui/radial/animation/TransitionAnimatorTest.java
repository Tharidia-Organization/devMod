package com.devmod.client.ui.radial.animation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TransitionAnimator")
class TransitionAnimatorTest {

    private static final float DELTA = 0.001f;
    private static final String NULL_REFLECTION_ERROR = "Unexpected reflection error";

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertThrowsNpe(ThrowingRunnable action) {
        assertThrows(NullPointerException.class, () -> {
            try {
                action.run();
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof NullPointerException npe) {
                    throw npe;
                }
                throw new RuntimeException(cause);
            } catch (Exception e) {
                throw new RuntimeException(NULL_REFLECTION_ERROR, e);
            }
        });
    }

    // ================================================================
    // INITIALIZATION TESTS
    // ================================================================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("starts with initial value")
        void startsWithInitialValue() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            assertEquals("A", animator.getCurrentValue());
        }

        @Test
        @DisplayName("starts not transitioning")
        void startsNotTransitioning() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            assertFalse(animator.isTransitioning());
        }

        @Test
        @DisplayName("starts with full progress")
        void startsWithFullProgress() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            assertEquals(1f, animator.getRawProgress(), DELTA);
        }

        @Test
        @DisplayName("starts with no previous value")
        void startsWithNoPreviousValue() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            assertNull(animator.getPreviousValue());
        }

        @Test
        @DisplayName("throws on null initial value")
        void throwsOnNullInitialValue() {
            assertThrowsNpe(() -> {
                var ctor = TransitionAnimator.class.getConstructor(Object.class, float.class);
                ctor.newInstance(new Object[]{null, 0.1f});
            });
        }

        @Test
        @DisplayName("throws on null easing function")
        void throwsOnNullEasingFunction() {
            assertThrowsNpe(() -> {
                java.lang.reflect.Constructor<?> target = null;
                for (var ctor : TransitionAnimator.class.getConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    if (params.length == 3 && params[0] == Object.class && params[1] == float.class) {
                        target = ctor;
                        break;
                    }
                }
                assertNotNull(target, "Expected 3-arg constructor not found");
                target.newInstance(new Object[]{"A", 0.1f, null});
            });
        }
    }

    // ================================================================
    // TRANSITION TESTS
    // ================================================================

    @Nested
    @DisplayName("Transition Behavior")
    class TransitionBehaviorTests {

        private TransitionAnimator<String> animator;

        @BeforeEach
        void setUp() {
            animator = new TransitionAnimator<>("A", 0.25f);
        }

        @Test
        @DisplayName("transitionTo starts transition")
        void transitionToStartsTransition() {
            boolean started = animator.transitionTo("B");

            assertTrue(started);
            assertTrue(animator.isTransitioning());
            assertEquals(0f, animator.getRawProgress(), DELTA);
        }

        @Test
        @DisplayName("transitionTo updates current value")
        void transitionToUpdatesCurrentValue() {
            animator.transitionTo("B");
            assertEquals("B", animator.getCurrentValue());
        }

        @Test
        @DisplayName("transitionTo stores previous value")
        void transitionToStoresPreviousValue() {
            animator.transitionTo("B");
            assertEquals("A", animator.getPreviousValue());
        }

        @Test
        @DisplayName("transitionTo returns false for same value")
        void transitionToReturnsFalseForSameValue() {
            boolean started = animator.transitionTo("A");

            assertFalse(started);
            assertFalse(animator.isTransitioning());
        }

        @Test
        @DisplayName("transitionTo throws on null value")
        void transitionToThrowsOnNull() {
            assertThrowsNpe(() -> {
                var method = TransitionAnimator.class.getMethod("transitionTo", Object.class);
                method.invoke(animator, new Object[]{null});
            });
        }
    }

    // ================================================================
    // UPDATE TESTS
    // ================================================================

    @Nested
    @DisplayName("Update Behavior")
    class UpdateBehaviorTests {

        @Test
        @DisplayName("update advances progress")
        void updateAdvancesProgress() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.25f);
            animator.transitionTo("B");

            animator.update();
            assertEquals(0.25f, animator.getRawProgress(), DELTA);

            animator.update();
            assertEquals(0.5f, animator.getRawProgress(), DELTA);
        }

        @Test
        @DisplayName("update completes transition")
        void updateCompletesTransition() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            animator.transitionTo("B");

            animator.update();
            animator.update();

            assertFalse(animator.isTransitioning());
            assertEquals(1f, animator.getRawProgress(), DELTA);
        }

        @Test
        @DisplayName("update clears previous value on completion")
        void updateClearsPreviousValueOnCompletion() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 1f);
            animator.transitionTo("B");

            animator.update();

            assertNull(animator.getPreviousValue());
        }

        @Test
        @DisplayName("update does nothing when not transitioning")
        void updateDoesNothingWhenNotTransitioning() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);

            animator.update();

            assertEquals(1f, animator.getRawProgress(), DELTA);
            assertFalse(animator.isTransitioning());
        }
    }

    // ================================================================
    // CROSS-FADE TESTS
    // ================================================================

    @Nested
    @DisplayName("Cross-Fade Alpha")
    class CrossFadeTests {

        @Test
        @DisplayName("outgoing alpha starts at 1")
        void outgoingAlphaStartsAt1() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            animator.transitionTo("B");

            assertEquals(1f, animator.getOutgoingAlpha(), DELTA);
        }

        @Test
        @DisplayName("incoming alpha starts at 0")
        void incomingAlphaStartsAt0() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            animator.transitionTo("B");

            assertEquals(0f, animator.getIncomingAlpha(), DELTA);
        }

        @Test
        @DisplayName("alphas are complementary")
        void alphasAreComplementary() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.25f);
            animator.transitionTo("B");

            for (int i = 0; i < 5; i++) {
                float outgoing = animator.getOutgoingAlpha();
                float incoming = animator.getIncomingAlpha();
                assertEquals(1f, outgoing + incoming, DELTA,
                    "outgoing + incoming should equal 1 at step " + i);
                animator.update();
            }
        }

        @Test
        @DisplayName("outgoing alpha is 0 when not transitioning")
        void outgoingAlphaIsZeroWhenNotTransitioning() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            assertEquals(0f, animator.getOutgoingAlpha(), DELTA);
        }

        @Test
        @DisplayName("incoming alpha is 1 when not transitioning")
        void incomingAlphaIsOneWhenNotTransitioning() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            assertEquals(1f, animator.getIncomingAlpha(), DELTA);
        }
    }

    // ================================================================
    // INTERPOLATION TESTS
    // ================================================================

    @Nested
    @DisplayName("Interpolation")
    class InterpolationTests {

        @Test
        @DisplayName("interpolate float values")
        void interpolateFloatValues() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            animator.transitionTo("B");

            // At progress 0
            assertEquals(0f, animator.interpolate(0f, 100f), DELTA);

            animator.update(); // progress 0.5

            assertEquals(50f, animator.interpolate(0f, 100f), DELTA);
        }

        @Test
        @DisplayName("interpolate int values")
        void interpolateIntValues() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            animator.transitionTo("B");
            animator.update(); // progress 0.5

            assertEquals(50, animator.interpolate(0, 100));
        }

        @Test
        @DisplayName("interpolate colors")
        void interpolateColors() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f);
            animator.transitionTo("B");
            animator.update(); // progress 0.5

            int fromColor = 0xFF000000; // Black
            int toColor = 0xFFFFFFFF;   // White

            int result = animator.interpolateColor(fromColor, toColor);

            // Should be approximately gray
            int r = (result >> 16) & 0xFF;
            int g = (result >> 8) & 0xFF;
            int b = result & 0xFF;

            assertTrue(r > 100 && r < 156, "Red should be ~128: " + r);
            assertTrue(g > 100 && g < 156, "Green should be ~128: " + g);
            assertTrue(b > 100 && b < 156, "Blue should be ~128: " + b);
        }
    }

    // ================================================================
    // IMMEDIATE SET TESTS
    // ================================================================

    @Nested
    @DisplayName("Immediate Set")
    class ImmediateSetTests {

        @Test
        @DisplayName("setImmediate updates value instantly")
        void setImmediateUpdatesInstantly() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            animator.setImmediate("B");

            assertEquals("B", animator.getCurrentValue());
            assertFalse(animator.isTransitioning());
        }

        @Test
        @DisplayName("setImmediate clears previous value")
        void setImmediateClearsPreviousValue() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            animator.transitionTo("B");
            animator.setImmediate("C");

            assertNull(animator.getPreviousValue());
        }

        @Test
        @DisplayName("setImmediate resets progress to 1")
        void setImmediateResetsProgress() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            animator.transitionTo("B");
            animator.setImmediate("C");

            assertEquals(1f, animator.getRawProgress(), DELTA);
        }
    }

    // ================================================================
    // SKIP TO END TESTS
    // ================================================================

    @Nested
    @DisplayName("Skip To End")
    class SkipToEndTests {

        @Test
        @DisplayName("skipToEnd completes transition instantly")
        void skipToEndCompletesInstantly() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            animator.transitionTo("B");
            animator.skipToEnd();

            assertFalse(animator.isTransitioning());
            assertEquals(1f, animator.getRawProgress(), DELTA);
        }

        @Test
        @DisplayName("skipToEnd clears previous value")
        void skipToEndClearsPreviousValue() {
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.1f);
            animator.transitionTo("B");
            animator.skipToEnd();

            assertNull(animator.getPreviousValue());
        }
    }

    // ================================================================
    // FACTORY METHOD TESTS
    // ================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("easeOut creates animator with ease-out easing")
        void easeOutCreatesEasedAnimator() {
            TransitionAnimator<String> animator = TransitionAnimator.easeOut("A", 0.5f);
            animator.transitionTo("B");
            animator.update();

            // Ease-out should be above linear at midpoint
            float easedProgress = animator.getEasedProgress();
            assertTrue(easedProgress > 0.5f, "ease-out should be above linear at midpoint");
        }

        @Test
        @DisplayName("easeInOut creates animator with ease-in-out easing")
        void easeInOutCreatesEasedAnimator() {
            TransitionAnimator<String> animator = TransitionAnimator.easeInOut("A", 0.5f);
            animator.transitionTo("B");
            animator.update();

            // Ease-in-out should be at 0.5 at midpoint
            float easedProgress = animator.getEasedProgress();
            assertEquals(0.5f, easedProgress, DELTA);
        }
    }

    // ================================================================
    // EASING APPLICATION TESTS
    // ================================================================

    @Nested
    @DisplayName("Easing Application")
    class EasingApplicationTests {

        @Test
        @DisplayName("custom easing is applied to progress")
        void customEasingApplied() {
            // Use a simple easing: always return 0.5
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f, t -> 0.5f);
            animator.transitionTo("B");
            animator.update(); // raw progress = 0.5

            assertEquals(0.5f, animator.getRawProgress(), DELTA);
            assertEquals(0.5f, animator.getEasedProgress(), DELTA);
        }

        @Test
        @DisplayName("easing affects interpolation")
        void easingAffectsInterpolation() {
            // Easing that returns t^2 (ease-in quadratic)
            TransitionAnimator<String> animator = new TransitionAnimator<>("A", 0.5f, Easing::easeInQuad);
            animator.transitionTo("B");
            animator.update(); // raw progress = 0.5

            // With ease-in-quad at 0.5, eased = 0.25
            assertEquals(25f, animator.interpolate(0f, 100f), DELTA);
        }
    }
}
