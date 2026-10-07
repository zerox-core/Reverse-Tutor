package com.reversetutor.preview

import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Guards one-time device tests whose preparation cannot be re-executed safely
 * after the instrumentation process is interrupted.
 *
 * On Huawei devices the platform may kill the instrumentation after a Compose
 * test rule tears down its Activity (RT-2026-007), which surfaces as an
 * AndroidJUnitRunner error instead of a test failure, and the runner then
 * retries the whole class in a surviving process. A class that wipes and
 * re-seeds local data before its phases would otherwise observe a session
 * title twice in the session list and fail on a stale assertion. This guard
 * aborts such a duplicate in-process execution with an explicit environment
 * error, so it is recorded as a device interruption rather than mixing stale
 * and freshly seeded state. Classes with self-restoring per-test rules are
 * unaffected: they never observe a second execution of one method instance.
 */
object ActivityInstanceRetryGuard : org.junit.rules.TestRule {

    private val executedMethods = mutableSetOf<String>()

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val key = "${description.testClass?.name}#${description.methodName}"
                check(executedMethods.add(key)) {
                    "Duplicate in-process execution of $key detected (RT-2026-007 host " +
                        "interruption retry). Aborting before re-running non-idempotent " +
                        "device preparation."
                }
                base.evaluate()
            }
        }
}
