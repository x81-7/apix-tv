cmake_minimum_required(VERSION 3.22.1)
project("v")

# ── استقبال المفاتيح من Gradle ────────────────────────────────────
foreach(K ENC_KEY HMAC_SECRET KEY_SALT EXT_KEY CLOUD_URL CLOUD_ANON_KEY SIGNATURE_HASH WORKER_URL)
    if(NOT DEFINED ${K})
        if(K STREQUAL "ENC_KEY")
            set(${K} "0000000000000000000000000000000000000000000000000000000000000000")
        elseif(K STREQUAL "HMAC_SECRET")
            set(${K} "default_hmac_secret_32bytes_here!")
        elseif(K STREQUAL "KEY_SALT")
            set(${K} "default_salt_16b__")
        elseif(K STREQUAL "EXT_KEY")
            set(${K} "0000000000000000000000000000000000000000000000000000000000000000")
        else()
            set(${K} "")
        endif()
    endif()
endforeach()

# ── تقسيم مفتاح التشفير على 4 ملفات مختلفة ──────────────────────
string(SUBSTRING "${ENC_KEY}" 0  16 ENC_P1)
string(SUBSTRING "${ENC_KEY}" 16 16 ENC_P2)
string(SUBSTRING "${ENC_KEY}" 32 16 ENC_P3)
string(SUBSTRING "${ENC_KEY}" 48 16 ENC_P4)

# ── تقسيم HMAC على ملفين ─────────────────────────────────────────
string(LENGTH "${HMAC_SECRET}" HMAC_LEN)
math(EXPR HMAC_HALF "${HMAC_LEN} / 2")
string(SUBSTRING "${HMAC_SECRET}" 0        ${HMAC_HALF} HMAC_P1)
string(SUBSTRING "${HMAC_SECRET}" ${HMAC_HALF} -1        HMAC_P2)

# ── تجميع الملفات ────────────────────────────────────────────────
add_library(v SHARED
    vault.cpp
    n1.cpp
    n2.cpp
    n3.cpp
    n4.cpp
)

# ── vault.cpp يحصل على: جزء 1 من ENC + HMAC_P1 + CloudURL + AnonKey + SigHash
target_compile_definitions(v PRIVATE
    V_A1="${ENC_P1}"
    V_B1="${HMAC_P1}"
    V_E="${CLOUD_URL}"
    V_F="${CLOUD_ANON_KEY}"
    V_H="${SIGNATURE_HASH}"
    V_W="${WORKER_URL}"
)

# ── n1.cpp يحصل على: جزء 2 من ENC + HMAC_P2
target_compile_definitions(v PRIVATE
    V_A2="${ENC_P2}"
    V_B2="${HMAC_P2}"
)

# ── n2.cpp يحصل على: جزء 3 من ENC + KEY_SALT
target_compile_definitions(v PRIVATE
    V_A3="${ENC_P3}"
    V_C="${KEY_SALT}"
)

# ── n3.cpp يحصل على: جزء 4 من ENC + EXT_KEY
target_compile_definitions(v PRIVATE
    V_A4="${ENC_P4}"
    V_D="${EXT_KEY}"
)

find_library(log-lib log)
target_link_libraries(v ${log-lib})