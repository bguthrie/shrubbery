clean:
	lein clean

test: clean
	lein with-profile dev test

test-1.5: clean
	lein with-profile 1.5 test

test-1.6: clean
	lein with-profile 1.6 test

test-1.7: clean
	lein with-profile 1.7 test

test-all: test-1.5 test-1.6 test-1.7 test

default: test-all

.PHONY: clean test-1.5 test-1.6 test-1.7 test test-all
