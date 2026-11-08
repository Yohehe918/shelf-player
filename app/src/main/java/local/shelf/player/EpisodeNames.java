// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Adapted 2026-11-08 from NOVA MediaLib fba68119be9b704bfa0cad78f7ca8422df0e5cfb.
// Four named-episode patterns retained; the caller supplies folder context.
package local.shelf.player;
import java.util.regex.*;
final class EpisodeNames {
 private static final String SEP_OPTIONAL = "[[\\p{Punct}&&[^()]]\\s]*+";
 private static final String SEP_MANDATORY = "[[\\p{Punct}&&[^()]]\\s]++";
 private static final Pattern[] PATTERNS={
Pattern.compile("(.+?)" + SEP_MANDATORY + "(?:s|seas|season)" + SEP_OPTIONAL + "(20\\d{2}|19\\d{2}|\\d{1,2})" + SEP_OPTIONAL + "(?:e|ep|episode)" + SEP_OPTIONAL + "(1?\\d{1,3})(?!\\d).*", Pattern.CASE_INSENSITIVE),
Pattern.compile("(.+?)" + SEP_OPTIONAL + "(?:\\[)" + SEP_OPTIONAL + "(\\d{1,3})" + SEP_OPTIONAL + "(?:x)" + SEP_OPTIONAL + "(\\d{1,3})" + SEP_OPTIONAL + "(?:\\])" + SEP_OPTIONAL + ".*", Pattern.CASE_INSENSITIVE),
Pattern.compile("(.+?)" + SEP_MANDATORY + "(20\\d{2}|19\\d{2}|\\d{1,2})" + SEP_OPTIONAL + "x" + SEP_MANDATORY + "(1?\\d{1,3})(?!\\d).*", Pattern.CASE_INSENSITIVE),
Pattern.compile("(.+?)" + SEP_MANDATORY + "(20\\d{2}|19\\d{2}|\\d{1,2})" + SEP_OPTIONAL + "x" + SEP_OPTIONAL + "(?!(?:264|265|720))(1?\\d{1,3})(?!\\d).*", Pattern.CASE_INSENSITIVE),
 };
 record Result(String name,int season,int episode){}
 static Result parse(String value){for(Pattern p:PATTERNS){Matcher m=p.matcher(value);if(m.matches())return new Result(m.group(1),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));}return null;}
}
