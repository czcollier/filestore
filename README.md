filestore
=========
This is an akka project.  It is a file storage service that allows consumers to store and retrieve arbitrarily sized files over HTTP.  It implements a de-duping mechanism such that files whose contents have the same sha1 hash will only be stored once.

The file data is stored directly on the filesystem, and a database of file hashes for de-duping is kept in a berkelydb database.

Filestore uses chunked HTTP to allow large files to be stored without conuming lots of memory, and to allow file storage operations to not consume a single thread for a great deal of time.  Since Akka and Spray are used, each request/response chunk is handled inside an actor receive method.
