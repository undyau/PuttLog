package com.undy.puttlog.domain

import com.undy.puttlog.data.Putt

data class CategoryStats(val made: Int, val attempts: Int) {
    val percent: Double get() = if (attempts == 0) 0.0 else made * 100.0 / attempts
}

data class PuttStats(
    val overall: CategoryStats,
    val short: CategoryStats,
    val c1x: CategoryStats,
    val c2: CategoryStats,
    val c3: CategoryStats
)

object StatsCalculator {
    fun compute(putts: List<Putt>): PuttStats {
        fun statsFor(filter: (Putt) -> Boolean): CategoryStats {
            val filtered = putts.filter(filter)
            return CategoryStats(filtered.count { it.made }, filtered.size)
        }

        return PuttStats(
            overall = statsFor { true },
            short = statsFor { categorize(it.distanceFeet) == PuttCategory.SHORT },
            c1x = statsFor { categorize(it.distanceFeet) == PuttCategory.C1X },
            c2 = statsFor { categorize(it.distanceFeet) == PuttCategory.C2 },
            c3 = statsFor { categorize(it.distanceFeet) == PuttCategory.C3 }
        )
    }
}
