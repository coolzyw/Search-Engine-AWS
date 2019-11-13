Instructions for running on murphy.wot.eecs.northwestern.edu:

    $ scl enable rh-python36 bash
    $ cd
    $ wget ftp://apache.cs.utah.edu/apache.org/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
    $ tar xzvf apache-maven-3.6.2-bin.tar.gz
    $ tar xzvf hw1_tester.tar.gz
    $ cd news-import-test

    ... copy in your news-import-1.0-SNAPSHOT.jar ...

    $ python3.6 -m venv virtualenv
    $ . virtualenv/bin/activate
    $ pip install elasticsearch requests-aws4auth requests
    $ python tester.py

    ... note that the command above may look frozen for a few minutes,
        but it's setting up a new mvn environment ...
