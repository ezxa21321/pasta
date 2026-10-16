cmake_minimum_required(VERSION 3.22.1)
project(airware_injector)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(airware_injector SHARED
    injector.cpp
    ptrace_utils.cpp
)

find_library(LOG_LIB log)
target_link_libraries(airware_injector ${LOG_LIB})
