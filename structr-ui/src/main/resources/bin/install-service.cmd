commons-daemon\amd64\prunsrv //IS//Structr --DisplayName="Structr" ^
	--Install="%CD%\commons-daemon\amd64\prunsrv.exe" ^
	--Startup=auto ^
	--StartPath="%CD%\.." ^
	--Classpath="lib\*" ^
	--JavaHome="%CD%\..\jdk-11" ^
	--Jvm="%CD%\..\jdk-11\bin\server\jvm.dll" ^
	--StartMode=jvm ^
	--StartClass=org.structr.Server ^
	--StopMode=jvm ^
	--StopClass=org.structr.Shutdown ^
	--LogPrefix=service ^
	--LogPath="%CD%\..\logs" ^
	--StdOutput="%CD%\..\logs\server.log" ^
	--StdError="%CD%\..\logs\server.log"