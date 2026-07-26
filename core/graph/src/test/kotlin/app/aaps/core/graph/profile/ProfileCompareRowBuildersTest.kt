package app.aaps.core.graph.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers [ProfileCompareRowBuilders]: the basal/ic/isf/target row builders (dedup + Σ row + change
 * branch) and buildProfileCompareData assembly. Profiles are mocked; no Android/Compose needed.
 */
class ProfileCompareRowBuildersTest {

    private val dateUtil: DateUtil = mock()
    private val profileUtil: ProfileUtil = mock()
    private val p1: Profile = mock()
    private val p2: Profile = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.formatHHMM(anyInt())).thenReturn("t")
        // pass mgdl through unchanged so isf/target values reflect the profile stubs
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenAnswer { it.arguments[0] as Double }
        whenever(p1.units).thenReturn(GlucoseUnit.MGDL)
        whenever(p2.units).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun basalRows_constantValues_giveOneRowPlusSum() {
        whenever(p1.getBasalTimeFromMidnight(anyInt())).thenReturn(1.0)
        whenever(p2.getBasalTimeFromMidnight(anyInt())).thenReturn(2.0)
        whenever(p1.percentageBasalSum()).thenReturn(24.0)
        whenever(p2.percentageBasalSum()).thenReturn(48.0)
        val rows = buildBasalRows(p1, p2, dateUtil)
        assertThat(rows).hasSize(2) // one change at hour 0 (values never change again) + the Σ row
        assertThat(rows.last().time).isEqualTo("∑")
    }

    @Test
    fun icRows_changingValues_addRowPerChange() {
        // value == hour → every hour differs from the previous → 24 rows
        whenever(p1.getIcTimeFromMidnight(anyInt())).thenAnswer { (it.arguments[0] as Int) / 3600.0 }
        whenever(p2.getIcTimeFromMidnight(anyInt())).thenReturn(5.0)
        val rows = buildIcRows(p1, p2, dateUtil)
        assertThat(rows).hasSize(24)
    }

    @Test
    fun isfRows_constantValues_giveSingleRow() {
        whenever(p1.getIsfMgdlTimeFromMidnight(anyInt())).thenReturn(50.0)
        whenever(p2.getIsfMgdlTimeFromMidnight(anyInt())).thenReturn(60.0)
        val rows = buildIsfRows(p1, p2, profileUtil, dateUtil)
        assertThat(rows).hasSize(1)
    }

    @Test
    fun targetRows_constantValues_giveSingleRangeRow() {
        whenever(p1.getTargetLowMgdlTimeFromMidnight(anyInt())).thenReturn(80.0)
        whenever(p1.getTargetHighMgdlTimeFromMidnight(anyInt())).thenReturn(120.0)
        whenever(p2.getTargetLowMgdlTimeFromMidnight(anyInt())).thenReturn(90.0)
        whenever(p2.getTargetHighMgdlTimeFromMidnight(anyInt())).thenReturn(110.0)
        val rows = buildTargetRows(p1, p2, dateUtil, profileUtil)
        assertThat(rows).hasSize(1)
        assertThat(rows.first().value1).contains(" - ")
    }

    @Test
    fun buildProfileCompareData_assemblesAllSections() {
        whenever(p1.getBasalTimeFromMidnight(anyInt())).thenReturn(1.0)
        whenever(p2.getBasalTimeFromMidnight(anyInt())).thenReturn(1.0)
        whenever(p1.percentageBasalSum()).thenReturn(24.0)
        whenever(p2.percentageBasalSum()).thenReturn(24.0)
        whenever(p1.getIcTimeFromMidnight(anyInt())).thenReturn(10.0)
        whenever(p2.getIcTimeFromMidnight(anyInt())).thenReturn(10.0)
        whenever(p1.getIsfMgdlTimeFromMidnight(anyInt())).thenReturn(50.0)
        whenever(p2.getIsfMgdlTimeFromMidnight(anyInt())).thenReturn(50.0)
        whenever(p1.getTargetLowMgdlTimeFromMidnight(anyInt())).thenReturn(80.0)
        whenever(p1.getTargetHighMgdlTimeFromMidnight(anyInt())).thenReturn(120.0)
        whenever(p2.getTargetLowMgdlTimeFromMidnight(anyInt())).thenReturn(80.0)
        whenever(p2.getTargetHighMgdlTimeFromMidnight(anyInt())).thenReturn(120.0)
        val rh: ResourceHelper = mock()
        whenever(rh.gs(anyInt())).thenReturn("u")
        val profileFunction: ProfileFunction = mock()
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)

        val data = buildProfileCompareData(p1, p2, "Base", "Effective", rh, dateUtil, profileUtil, profileFunction)

        assertThat(data.baseName).isEqualTo("Base")
        assertThat(data.effectiveName).isEqualTo("Effective")
        assertThat(data.baseProfile).isSameInstanceAs(p1)
        assertThat(data.basalRows).isNotEmpty()
        assertThat(data.icRows).isNotEmpty()
        assertThat(data.isfRows).isNotEmpty()
        assertThat(data.targetRows).isNotEmpty()
    }
}
