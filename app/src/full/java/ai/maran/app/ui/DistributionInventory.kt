package ai.maran.app.ui

import android.content.Context

internal fun distributionAppInventoryResult(context:Context):String =
    DeviceAppInventory.listLaunchable(context)
