package com.jp.freedom.sync

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object SyncScheduler {
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, AndroidSyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(job)
    }

    const val JOB_ID = 7041
}
