package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.calc.Stamina
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaminaTest {

    @Test
    fun 要件定義書の例_最大100_間隔5分_50を14時に記録_14時30分は56() {
        val r = stamina(1, 1, max = 100, interval = 5, value = 50, at = jst(2026, 8, 25, 14, 0))
        val clock = Clock.fixed(jst(2026, 8, 25, 14, 30), JST)
        assertEquals(56, Stamina.currentValue(r, clock.instant()))
    }

    @Test
    fun 端数は切り捨てる_4分59秒では増えず_5分で1増える() {
        val r = stamina(1, 1, value = 50)
        assertEquals(50, Stamina.currentValue(r, r.recordedAt.plusSeconds(299)))
        assertEquals(51, Stamina.currentValue(r, r.recordedAt.plusSeconds(300)))
    }

    @Test
    fun 最大を超えない() {
        val r = stamina(1, 1, max = 100, interval = 5, value = 50)
        assertEquals(100, Stamina.currentValue(r, jst(2027, 1, 1)))
        assertTrue(Stamina.isFull(r, jst(2027, 1, 1)))
        assertEquals(1f, Stamina.fraction(r, jst(2027, 1, 1)))
    }

    @Test
    fun 記録の値が最大より大きくても最大として扱う() {
        val r = stamina(1, 1, max = 100, value = 250)
        assertEquals(100, Stamina.currentValue(r, r.recordedAt))
        assertEquals(r.recordedAt, Stamina.fullAt(r))
    }

    @Test
    fun 記録の時刻が未来なら経過は0として扱う() {
        val r = stamina(1, 1, value = 50, at = jst(2026, 8, 25, 14, 0))
        assertEquals(50, Stamina.currentValue(r, jst(2026, 8, 25, 13, 0)))
    }

    @Test
    fun 回復の間隔が0以下なら増えず_満タンの時刻も無い() {
        val r = stamina(1, 1, interval = 0, value = 50)
        assertEquals(50, Stamina.currentValue(r, jst(2027, 1, 1)))
        assertNull(Stamina.fullAt(r))
        assertNull(Stamina.fullNoticeAt(r))
    }

    @Test
    fun 満タンの時刻は_足りない分かける間隔() {
        val r = stamina(1, 1, max = 100, interval = 5, value = 50, at = jst(2026, 8, 25, 14, 0))
        // 50 足りない × 5 分 = 250 分 = 4 時間 10 分
        assertEquals(jst(2026, 8, 25, 18, 10), Stamina.fullAt(r))
        assertFalse(Stamina.isFull(r, jst(2026, 8, 25, 18, 9)))
        assertTrue(Stamina.isFull(r, jst(2026, 8, 25, 18, 10)))
    }

    @Test
    fun 満タンの通知は_設定が切のときと_記録の時点で満タンのときは出さない() {
        assertNull(Stamina.fullNoticeAt(stamina(1, 1, notifyOnFull = false)))
        assertNull(Stamina.fullNoticeAt(stamina(1, 1, max = 100, value = 100)))
        assertEquals(jst(2026, 8, 25, 18, 10), Stamina.fullNoticeAt(stamina(1, 1)))
    }

    @Test
    fun 割合の通知の値は切り上げる() {
        assertEquals(144, Stamina.thresholdValue(160, 90))
        assertEquals(90, Stamina.thresholdValue(100, 90))
        assertEquals(2, Stamina.thresholdValue(3, 50))
    }

    @Test
    fun 割合の通知の時刻() {
        val r = stamina(1, 1, max = 100, interval = 5, value = 50, threshold = 90)
        // 90 まで 40 足りない × 5 分 = 200 分
        assertEquals(jst(2026, 8, 25, 17, 20), Stamina.thresholdAt(r))
    }

    @Test
    fun 割合の通知は_もう超えているときと_範囲の外の設定では出さない() {
        assertNull(Stamina.thresholdAt(stamina(1, 1, value = 95, threshold = 90)))
        assertNull(Stamina.thresholdAt(stamina(1, 1, threshold = null)))
        assertNull(Stamina.thresholdAt(stamina(1, 1, threshold = 100)))
        assertNull(Stamina.thresholdAt(stamina(1, 1, threshold = 0)))
        // 最大が小さくて、割合の値が最大と同じになるなら、満タンの通知と重なるので出さない
        assertNull(Stamina.thresholdAt(stamina(1, 1, max = 3, value = 0, threshold = 99)))
    }

    @Test
    fun 編集で何も変えなければ_記録の値と時刻を残す() {
        val old = stamina(1, 1, value = 50, at = jst(2026, 8, 25, 14, 0))
        val now = jst(2026, 8, 25, 14, 32)
        val saved = Stamina.edited(old, "BP", 100, 5, enteredValue = 56, notifyOnFull = false, notifyThresholdPercent = 80, now = now)
        assertEquals("BP", saved.resourceName)
        assertEquals(50, saved.recordedValue)
        assertEquals(old.recordedAt, saved.recordedAt)
        assertFalse(saved.notifyOnFull)
        assertEquals(80, saved.notifyThresholdPercent)
    }

    @Test
    fun 回復の間隔を変えたら_変えた時点を新しい基準にして数え直す() {
        val old = stamina(1, 1, max = 100, interval = 5, value = 50, at = jst(2026, 8, 25, 14, 0))
        val now = jst(2026, 8, 25, 14, 30)
        // 画面は今の値(56)を入れた状態で開く。間隔だけ 5 → 10 分に変える
        val saved = Stamina.edited(old, "AP", 100, 10, enteredValue = 56, notifyOnFull = true, notifyThresholdPercent = null, now = now)
        assertEquals(56, saved.recordedValue)
        assertEquals(now, saved.recordedAt)
        // 以後は新しい間隔で増える
        assertEquals(57, Stamina.currentValue(saved, jst(2026, 8, 25, 14, 40)))
    }

    @Test
    fun 値を打ち直したら_その値と今の時刻で記録する_最大は超えない() {
        val old = stamina(1, 1, max = 100, value = 50)
        val now = jst(2026, 8, 25, 15, 0)
        val saved = Stamina.edited(old, "AP", 100, 5, enteredValue = 999, notifyOnFull = true, notifyThresholdPercent = null, now = now)
        assertEquals(100, saved.recordedValue)
        assertEquals(now, saved.recordedAt)
    }
}
