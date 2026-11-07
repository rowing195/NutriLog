package com.watson.nutrilog

import com.watson.nutrilog.data.db.drinkVolumeMl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 資料健檢的缺水量清單只列份量寫了容量的品項，帶進輸入框的也是這個數字。 */
class DrinkVolumeTest {

    @Test fun readsCommonWritings() {
        assertEquals(700.0, drinkVolumeMl("大杯 700ml 全糖")!!, 1e-9)
        assertEquals(600.0, drinkVolumeMl("1 瓶 (600 mL)")!!, 1e-9)
        assertEquals(350.0, drinkVolumeMl("350cc")!!, 1e-9)
        assertEquals(250.0, drinkVolumeMl("1 杯 250 毫升")!!, 1e-9)
        assertEquals(330.5, drinkVolumeMl("330.5ML")!!, 1e-9)
    }

    @Test fun noVolumeMeansNotListed() {
        assertNull(drinkVolumeMl("1 碗"))
        assertNull(drinkVolumeMl(""))
        assertNull(drinkVolumeMl("200 g"))
    }

    /** 飲料店的大杯叫 L，「L 杯」不能被讀成一公升。 */
    @Test fun sizeLetterIsNotLitre() {
        assertNull(drinkVolumeMl("L 杯 半糖"))
        assertNull(drinkVolumeMl("1 L"))
    }
}
