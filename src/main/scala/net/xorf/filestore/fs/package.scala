package net.xorf.filestore

/**
 * Code in & below here handles file I/O stuff
 * for writing files to temporary and permanent storage.
 *
 * There are two parallel sub-packages containing different implementations
 * of these classes and traits.  One using regular blocking calls in the
 * standard java.io package (stdio), and one using non-blocking operations
 * from the java.nio API (nio).
 *
 * I did this in order to get a basic version working using the familiar
 * blocking calls, then swap in the nio calls once functional.  The intent
 * is that the blocking calls will obsolesce.
 *
 */
package object fs {  }
