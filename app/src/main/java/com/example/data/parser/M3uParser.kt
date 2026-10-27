package com.example.data.parser

import com.example.data.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

object M3uParser {

    private val LOGO_PATTERN = Pattern.compile("""tvg-logo=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val NAME_PATTERN = Pattern.compile("""tvg-name=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val GROUP_PATTERN = Pattern.compile("""group-title=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val CHNO_PATTERN = Pattern.compile("""tvg-chno=["'](\d+)["']""", Pattern.CASE_INSENSITIVE)

    fun parse(inputStream: InputStream, playlistId: String, playlistName: String): List<Channel> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val channels = ArrayList<Channel>(1024)

        var currentExtInf: String? = null
        var sequentialNumber = 1

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    currentExtInf = line
                } else if (!line.startsWith("#")) {
                    // This is a stream URL
                    val streamUrl = line
                    val extInf = currentExtInf ?: ""

                    var channelName = ""
                    val commaIndex = extInf.lastIndexOf(',')
                    if (commaIndex != -1 && commaIndex < extInf.length - 1) {
                        channelName = extInf.substring(commaIndex + 1).trim()
                    }

                    // Extract tvg-name if channelName is still empty
                    if (channelName.isEmpty()) {
                        val mName = NAME_PATTERN.matcher(extInf)
                        if (mName.find()) {
                            channelName = mName.group(1)?.trim() ?: ""
                        }
                    }

                    // Fallback to URL tail or sequential name if still empty
                    if (channelName.isEmpty()) {
                        channelName = "Channel $sequentialNumber"
                    }

                    // Extract tvg-logo
                    var logoUrl: String? = null
                    val mLogo = LOGO_PATTERN.matcher(extInf)
                    if (mLogo.find()) {
                        val parsed = mLogo.group(1)?.trim()
                        if (!parsed.isNullOrEmpty() && (parsed.startsWith("http://") || parsed.startsWith("https://"))) {
                            logoUrl = parsed
                        }
                    }

                    // Extract group-title
                    var groupTitle = ""
                    val mGroup = GROUP_PATTERN.matcher(extInf)
                    if (mGroup.find()) {
                        groupTitle = mGroup.group(1)?.trim() ?: ""
                    }

                    // Extract channel number
                    var channelNumber = sequentialNumber
                    val mChno = CHNO_PATTERN.matcher(extInf)
                    if (mChno.find()) {
                        mChno.group(1)?.toIntOrNull()?.let {
                            if (it > 0) channelNumber = it
                        }
                    }

                    val id = "${playlistId}_${sequentialNumber}_${streamUrl.hashCode()}"

                    channels.add(
                        Channel(
                            id = id,
                            playlistId = playlistId,
                            playlistName = playlistName,
                            channelNumber = channelNumber,
                            name = channelName,
                            logoUrl = logoUrl,
                            groupTitle = groupTitle,
                            streamUrl = streamUrl,
                            isFavorite = false
                        )
                    )

                    currentExtInf = null
                    sequentialNumber++
                }
            }
        }

        return channels
    }
}
