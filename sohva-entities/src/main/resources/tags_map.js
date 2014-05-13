function(doc) {
  if(doc.type === "entity") {
    // only emit entity documents
    if(doc.tag) {
      emit(doc.tag, doc._id);
    } else {
      emit(null, doc,_id);
    }
  }
}
