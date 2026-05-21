package com.jdcr.jdcrble.core.communicator

import kotlinx.coroutines.flow.SharedFlow

interface JdcrBleCommunicator {

    fun sendAction(
        action: JdcrBleCommunicatorAction,
        inMainThread: Boolean = false,
        onComplete: ((Result<JdcrBleCommunicatorActionResult>) -> Unit)?
    )

    fun getNotificationDataFlow(): SharedFlow<NotificationData>

    fun release()

}