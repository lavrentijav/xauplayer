package ru.fire_core.xauplayer.core.logger

import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

object HttpLogger {
    /**
     * Маскирует конфиденциальные данные в строке
     */
    fun maskSensitiveData(text: String): String {
        var masked = text
        
        // Маскируем токены (Bearer токены, access_token, refresh_token)
        masked = masked.replace(Regex("""Bearer\s+[A-Za-z0-9\-_]+"""), "Bearer ***")
        masked = masked.replace(Regex(""""access_token"\s*:\s*"[^"]+""""), """"access_token": "***"""")
        masked = masked.replace(Regex(""""refresh_token"\s*:\s*"[^"]+""""), """"refresh_token": "***"""")
        masked = masked.replace(Regex(""""token"\s*:\s*"[^"]+""""), """"token": "***"""")
        
        // Маскируем пароли
        masked = masked.replace(Regex(""""password"\s*:\s*"[^"]+""""), """"password": "***"""")
        masked = masked.replace(Regex(""""pwd"\s*:\s*"[^"]+""""), """"pwd": "***"""")
        
        // Маскируем Authorization заголовки
        masked = masked.replace(Regex("""Authorization:\s*[^\n]+"""), "Authorization: ***")
        
        return masked
    }
    
    /**
     * Логирует HTTP запрос с маскированием конфиденциальных данных
     */
    fun logRequest(logger: AppLogger, request: Request, tag: String = "HTTP") {
        try {
            val url = request.url.toString()
            val method = request.method
            
            val headers = StringBuilder()
            request.headers.names().forEach { name ->
                val value = request.headers.get(name) ?: ""
                val maskedValue = if (name.equals("Authorization", ignoreCase = true)) {
                    "***"
                } else {
                    value
                }
                headers.append("  $name: $maskedValue\n")
            }
            
            val body = request.body?.let { body ->
                try {
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    val bodyString = buffer.readUtf8()
                    maskSensitiveData(bodyString)
                } catch (e: IOException) {
                    "[Unable to read body: ${e.message}]"
                }
            } ?: "[No body]"
            
            val requestLog = buildString {
                append("REQUEST:\n")
                append("  Method: $method\n")
                append("  URL: $url\n")
                append("  Headers:\n$headers")
                append("  Body: $body\n")
            }
            
            logger.debug(tag, maskSensitiveData(requestLog))
        } catch (e: Exception) {
            logger.error(tag, "Failed to log request", e)
        }
    }
    
    /**
     * Логирует HTTP ответ с маскированием конфиденциальных данных
     */
    fun logResponse(logger: AppLogger, response: Response, tag: String = "HTTP") {
        try {
            val url = response.request.url.toString()
            val code = response.code
            val message = response.message
            val isSuccessful = response.isSuccessful
            
            val headers = StringBuilder()
            response.headers.names().forEach { name ->
                val value = response.headers.get(name) ?: ""
                val maskedValue = if (name.equals("Authorization", ignoreCase = true)) {
                    "***"
                } else {
                    value
                }
                headers.append("  $name: $maskedValue\n")
            }
            
            val body = response.body?.let { body ->
                try {
                    val source = body.source()
                    source.request(Long.MAX_VALUE)
                    val buffer = source.buffer
                    val bodyString = buffer.clone().readUtf8()
                    maskSensitiveData(bodyString)
                } catch (e: IOException) {
                    "[Unable to read body: ${e.message}]"
                }
            } ?: "[No body]"
            
            val responseLog = buildString {
                append("RESPONSE:\n")
                append("  URL: $url\n")
                append("  Code: $code\n")
                append("  Message: $message\n")
                append("  Successful: $isSuccessful\n")
                append("  Headers:\n$headers")
                append("  Body: $body\n")
            }
            
            if (isSuccessful) {
                logger.debug(tag, maskSensitiveData(responseLog))
            } else {
                logger.error(tag, maskSensitiveData(responseLog))
            }
        } catch (e: Exception) {
            logger.error(tag, "Failed to log response", e)
        }
    }
    
    /**
     * Логирует ошибку HTTP запроса с подробностями
     */
    fun logError(
        logger: AppLogger,
        request: Request?,
        response: Response?,
        throwable: Throwable?,
        tag: String = "HTTP"
    ) {
        try {
            val errorLog = buildString {
                append("HTTP ERROR:\n")
                
                request?.let {
                    append("REQUEST:\n")
                    append("  Method: ${it.method}\n")
                    append("  URL: ${it.url}\n")
                    val headers = StringBuilder()
                    it.headers.names().forEach { name ->
                        val value = it.headers.get(name) ?: ""
                        val maskedValue = if (name.equals("Authorization", ignoreCase = true)) {
                            "***"
                        } else {
                            value
                        }
                        headers.append("  $name: $maskedValue\n")
                    }
                    append("  Headers:\n$headers")
                    
                    it.body?.let { body ->
                        try {
                            val buffer = Buffer()
                            body.writeTo(buffer)
                            val bodyString = buffer.readUtf8()
                            append("  Body: ${maskSensitiveData(bodyString)}\n")
                        } catch (e: IOException) {
                            append("  Body: [Unable to read: ${e.message}]\n")
                        }
                    } ?: append("  Body: [No body]\n")
                } ?: append("REQUEST: [No request]\n")
                
                response?.let {
                    append("\nRESPONSE:\n")
                    append("  Code: ${it.code}\n")
                    append("  Message: ${it.message}\n")
                    val headers = StringBuilder()
                    it.headers.names().forEach { name ->
                        val value = it.headers.get(name) ?: ""
                        val maskedValue = if (name.equals("Authorization", ignoreCase = true)) {
                            "***"
                        } else {
                            value
                        }
                        headers.append("  $name: $maskedValue\n")
                    }
                    append("  Headers:\n$headers")
                    
                    it.body?.let { body ->
                        try {
                            val source = body.source()
                            source.request(Long.MAX_VALUE)
                            val buffer = source.buffer
                            val bodyString = buffer.clone().readUtf8()
                            append("  Body: ${maskSensitiveData(bodyString)}\n")
                        } catch (e: IOException) {
                            append("  Body: [Unable to read: ${e.message}]\n")
                        }
                    } ?: append("  Body: [No body]\n")
                } ?: append("\nRESPONSE: [No response]\n")
                
                throwable?.let {
                    append("\nEXCEPTION:\n")
                    append("  Type: ${it.javaClass.name}\n")
                    append("  Message: ${it.message}\n")
                    append("  StackTrace:\n")
                    it.stackTrace.take(10).forEach { element ->
                        append("    ${element.toString()}\n")
                    }
                } ?: append("\nEXCEPTION: [No exception]\n")
            }
            
            logger.error(tag, maskSensitiveData(errorLog), throwable)
        } catch (e: Exception) {
            logger.error(tag, "Failed to log HTTP error", e)
        }
    }
}

