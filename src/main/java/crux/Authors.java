package crux;

final class Authors {
  // TODO: Add author information.

  static final Author[] all = {new Author("Xiaotian Fang", "57532694", "xiaotif1"),
          new Author("Yi Sun", "63355975", "SUNY17")};
}


final class Author {
  final String name;
  final String studentId;
  final String uciNetId;

  Author(String name, String studentId, String uciNetId) {
    this.name = name;
    this.studentId = studentId;
    this.uciNetId = uciNetId;
  }
}
