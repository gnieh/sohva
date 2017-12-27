/*
* This file is part of the sohva project.
* Copyright (c) 2016 Lucas Satabin
*
* Licensed under the Apache License, Version 2.0 (the "License");
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
package mango

final case class SearchResult[T](docs: Vector[T], warning: Option[String], execution_stats: Option[ExecutionStats])

final case class ExecutionStats(
    total_keys_examined: Int,
    total_docs_examined: Int,
    total_quorum_docs_examined: Int,
    results_returned: Int,
    execution_time_ms: Double)
