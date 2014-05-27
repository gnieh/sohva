function(doc) {
  if(doc["sohva-entities-type"] === "component" &&
      doc["sohva-entities-entity"] &&
      doc["sohva-entities-name"]) {
    emit([doc["sohva-entities-entity"], doc["sohva-entities-name"]], doc._id);
  }
}
