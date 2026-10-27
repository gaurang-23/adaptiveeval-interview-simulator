@echo off
if not exist "%JAVA_HOME%\bin\java.exe" (
  if exist "C:\Users\gaura\.jdks\temurin-21.0.11\bin\java.exe" (
    set "JAVA_HOME=C:\Users\gaura\.jdks\temurin-21.0.11"
  ) else if exist "C:\Program Files\Java\jdk-24\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-24"
  )
)
set "MVN_BIN=C:\Users\gaura\.m2\wrapper\dists\apache-maven-3.9.9-bin\4nf9hui3q3djbarqar9g711ggc\apache-maven-3.9.9\bin\mvn.cmd"
if exist "%MVN_BIN%" (
  call "%MVN_BIN%" %*
) else (
  mvn %*
)
