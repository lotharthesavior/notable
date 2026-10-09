# Thin wrappers around Gradle so tooling that looks for `make test`/`make build` can run the
# project's checks. Uses Android Studio's bundled JDK when JAVA_HOME is not set.

ANDROID_STUDIO_JDK := /Applications/Android Studio.app/Contents/jbr/Contents/Home
ifeq ($(strip $(JAVA_HOME)),)
  ifeq ($(shell test -x "$(ANDROID_STUDIO_JDK)/bin/java" && echo yes),yes)
    export JAVA_HOME := $(ANDROID_STUDIO_JDK)
  endif
endif

GRADLE := ./gradlew --console=plain

.PHONY: typecheck test build

typecheck:
	$(GRADLE) :app:compileDebugKotlin

test:
	$(GRADLE) :app:testDebugUnitTest

build:
	$(GRADLE) :app:assembleDebug
