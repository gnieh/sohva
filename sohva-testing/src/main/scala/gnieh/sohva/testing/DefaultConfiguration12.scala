/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License")
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva
package testing

import java.io.File

/** The default couchdb database, used as a basis for the extra instance.
 *
 *  @author Lucas Satabin
 */
class DefaultConfiguration12(datadir: File, logdir: File, rundir: File) extends Configuration(
  Map(
    "couchdb" -> Map(
      "database_dir" -> datadir.getCanonicalPath,
      "view_index_dir" -> datadir.getCanonicalPath,
      "max_document_size" -> "4294967296",
      "os_process_timeout" -> "5000",
      "max_dbs_open" -> "100",
      "delayed_commits" -> "true",
      "uri_file" -> new File(rundir, "couch.uri").getCanonicalPath,
      "file_compression" -> "snappy"
    ),
    "database_compaction" -> Map(
      "doc_buffer_size" -> "524288",
      "checkpoint_after" -> "5242880"
    ),
    "view_compaction" -> Map(
      "keyvalue_buffer_size" -> "2097152"
    ),
    "httpd" -> Map(
      "port" -> "15984",
      "bind_address" -> "127.0.0.1",
      "authentication_handlers" -> "{couch_httpd_oauth, oauth_authentication_handler}, {couch_httpd_auth, cookie_authentication_handler}, {couch_httpd_auth, default_authentication_handler}",
      "default_handler" -> "{couch_httpd_db, handle_request}",
      "secure_rewrites" -> "true",
      "vhost_global_handlers" -> "_utils, _uuids, _session, _oauth, _users",
      "allow_jsonp" -> "false",
      "log_max_chunk_size" -> "1000000"
    ),
    "ssl" -> Map(
      "port" -> "16984"
    ),
    "log" -> Map(
      "file" -> new File(logdir, "couch.log").getCanonicalPath,
      "level" -> "debug",
      "include_sasl" -> "true"
    ),
    "couch_httpd_auth" -> Map(
      "authentication_db" -> "_users",
      "authentication_redirect" -> "/_utils/session.html",
      "require_valid_user" -> "false",
      "timeout" -> "600",
      "auth_cache_size" -> "50",
      "allow_persistent_cookies" -> "false"
    ),
    "couch_httpd_oauth" -> Map(
      "use_users_db" -> "false"
    ),
    "query_server_config" -> Map(
      "reduce_limit" -> "true",
      "os_process_limit" -> "25"
    ),
    "daemons" -> Map(
      "view_manager" -> "{couch_view, start_link, []}",
      "external_manager" -> "{couch_external_manager, start_link, []}",
      "query_servers" -> "{couch_query_servers, start_link, []}",
      "vhosts" -> "{couch_httpd_vhost, start_link, []}",
      "httpd" -> "{couch_httpd, start_link, []}",
      "stats_aggregator" -> "{couch_stats_aggregator, start, []}",
      "stats_collector" -> "{couch_stats_collector, start, []}",
      "uuids" -> "{couch_uuids, start, []}",
      "auth_cache" -> "{couch_auth_cache, start_link, []}",
      "replication_manager" -> "{couch_replication_manager, start_link, []}",
      "os_daemons" -> "{couch_os_daemons, start_link, []}",
      "compaction_daemon" -> "{couch_compaction_daemon, start_link, []}"
    ),
    "httpd_global_handlers" -> Map(
      "/" -> "{couch_httpd_misc_handlers, handle_welcome_req, <<\"Welcome\">>}",
      "_all_dbs" -> "{couch_httpd_misc_handlers, handle_all_dbs_req}",
      "_active_tasks" -> "{couch_httpd_misc_handlers, handle_task_status_req}",
      "_config" -> "{couch_httpd_misc_handlers, handle_config_req}",
      "_replicate" -> "{couch_httpd_replicator, handle_req}",
      "_uuids" -> "{couch_httpd_misc_handlers, handle_uuids_req}",
      "_restart" -> "{couch_httpd_misc_handlers, handle_restart_req}",
      "_stats" -> "{couch_httpd_stats_handlers, handle_stats_req}",
      "_log" -> "{couch_httpd_misc_handlers, handle_log_req}",
      "_session" -> "{couch_httpd_auth, handle_session_req}",
      "_oauth" -> "{couch_httpd_oauth, handle_oauth_req}"
    ),
    "httpd_db_handlers" -> Map(
      "_view_cleanup" -> "{couch_httpd_db, handle_view_cleanup_req}",
      "_compact" -> "{couch_httpd_db, handle_compact_req}",
      "_design" -> "{couch_httpd_db, handle_design_req}",
      "_temp_view" -> "{couch_httpd_view, handle_temp_view_req}",
      "_changes" -> "{couch_httpd_db, handle_changes_req}"
    ),
    "httpd_design_handlers" -> Map(
      "_view" -> "{couch_httpd_view, handle_view_req}",
      "_show" -> "{couch_httpd_show, handle_doc_show_req}",
      "_list" -> "{couch_httpd_show, handle_view_list_req}",
      "_info" -> "{couch_httpd_db,   handle_design_info_req}",
      "_rewrite" -> "{couch_httpd_rewrite, handle_rewrite_req}",
      "_update" -> "{couch_httpd_show, handle_doc_update_req}"
    ),
    "uuids" -> Map(
      "algorithm" -> "sequential"
    ),
    "stats" -> Map(
      "rate" -> "1000",
      "samples" -> "[0, 60, 300, 900]"
    ),
    "attachments" -> Map(
      "compression_level" -> "8",
      "compressible_types" -> "text/*, application/javascript, application/json, application/xml"
    ),
    "replicator" -> Map(
      "db" -> "_replicator",
      "max_replication_retry_count" -> "10",
      "worker_processes" -> "4",
      "worker_batch_size" -> "500",
      "http_connections" -> "20",
      "connection_timeout" -> "30000",
      "retries_per_request" -> "10",
      "socket_options" -> "[{keepalive, true}, {nodelay, false}]",
      "verify_ssl_certificates" -> "false",
      "ssl_certificate_max_depth" -> "3"
    ),
    "compaction_daemon" -> Map(
      "check_interval" -> "300",
      "min_file_size" -> "131072"
    ),
    "compactions" -> Map()
  )
)

