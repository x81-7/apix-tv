cmake_minimum_required(VERSION 3.22.1)
project("v")

# استقبال المفاتيح من Gradle
if(NOT DEFINED ENC_KEY)
    set(ENC_KEY "0000000000000000000000000000000000000000000000000000000000000000")
endif()
if(NOT DEFINED HMAC_SECRET)
    set(HMAC_SECRET "default_hmac_secret_32bytes_here!")
endif()
if(NOT DEFINED KEY_SALT)
    set(KEY_SALT "default_salt_value_16b")
endif()
if(NOT DEFINED EXT_KEY)
    set(EXT_KEY "7a3f8b9d4e2c1a5f6b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a")
endif()
if(NOT DEFINED CLOUD_URL)
    set(CLOUD_URL "")
endif()
if(NOT DEFINED CLOUD_ANON_KEY)
    set(CLOUD_ANON_KEY "")
endif()

add_library(v SHARED vault.cpp)

target_compile_definitions(v PRIVATE
    V_A="${ENC_KEY}"
    V_B="${HMAC_SECRET}"
    V_C="${KEY_SALT}"
    V_D="${EXT_KEY}"
    V_E="${CLOUD_URL}"
    V_F="${CLOUD_ANON_KEY}"
)

find_library(log-lib log)
target_link_libraries(v ${log-lib})