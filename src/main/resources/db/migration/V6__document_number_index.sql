-- Finds other attempts with the same document for the opened case.
create index verification_case_document_number_idx on verification_case (document_number);
