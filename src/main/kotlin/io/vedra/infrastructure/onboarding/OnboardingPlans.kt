package io.vedra.infrastructure.onboarding

data class PlanLimits(
    val maxProjects: Int,
    val maxIndustries: Int,
    val monthlyIngest: Int,
    val monthlyAnswers: Int,
)

object OnboardingPlans {
    const val DEFAULT = "Free"
    val LIMITS =
        mapOf(
            DEFAULT to PlanLimits(maxProjects = 3, maxIndustries = 3, monthlyIngest = 500, monthlyAnswers = 100),
        )

    fun limitsFor(plan: String = DEFAULT): PlanLimits = LIMITS[plan] ?: LIMITS[DEFAULT]!!
}
