package ai.maran.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class LocalWorkerStore(context:Context) {
    private val prefs=context.getSharedPreferences("local_workers",Context.MODE_PRIVATE)
    private val gson=Gson()

    private val defaults=listOf(
        WorkerDto("local-tourism","Tourism Specialist",listOf("tourism","itinerary","quotation","travel"),listOf("local_ai")),
        WorkerDto("local-research","Research Specialist",listOf("research","web","analysis"),listOf("local_ai")),
        WorkerDto("local-marketing","Marketing Specialist",listOf("marketing","seo","social","content"),listOf("local_ai")),
        WorkerDto("local-documents","Document Specialist",listOf("documents","ocr","pdf","drafting"),listOf("local_ai")),
        WorkerDto("local-coding","Coding Specialist",listOf("coding","github","testing"),listOf("local_ai")),
        WorkerDto("local-accounting","Accounting Specialist",listOf("invoice","gst","tally"),listOf("local_ai"))
    )

    fun list():List<WorkerDto> {
        val raw=prefs.getString("workers",null)
        if(raw.isNullOrBlank()) {
            save(defaults)
            return defaults
        }
        return runCatching {
            val type=object:TypeToken<List<WorkerDto>>(){}.type
            gson.fromJson<List<WorkerDto>>(raw,type)
        }.getOrElse {
            save(defaults)
            defaults
        }
    }

    fun add(name:String,skills:List<String>):WorkerDto {
        val worker=WorkerDto(
            id="local-"+UUID.randomUUID().toString(),
            name=name.trim(),
            skills=skills.map(String::trim).filter(String::isNotBlank).distinct(),
            permissions=listOf("local_ai")
        )
        save(list()+worker)
        return worker
    }

    fun remove(id:String) {
        save(list().filterNot { it.id==id })
    }

    private fun save(items:List<WorkerDto>) {
        prefs.edit().putString("workers",gson.toJson(items)).apply()
    }
}
