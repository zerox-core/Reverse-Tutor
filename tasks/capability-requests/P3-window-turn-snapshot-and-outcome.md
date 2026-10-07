# 鍐荤粨灞傝兘鍔涚敵璇凤細P3 绐楀彛鍥炲悎蹇収涓庣粨鏋勫寲鍥炲悎缁撴灉

> 鐘舵€侊細**proposed锛屾湭鎵瑰噯锛屼笉寰楀疄鏂?*
>
> 瀵瑰簲鍒囩墖锛歚newmp` 鍘熺敓 Android 鐢熶骇鍒嗘敮 鈥斺€?`native-companion-memory-topology`锛圱ask 11锛?> 鐢宠鏃ユ湡锛?026-08-24
> 鍏宠仈鏂囨。锛歔native-companion-memory-topology-design.md](../native-companion-memory-topology-design.md) 搂6/搂7銆乕native-backend-protocol-data-contract-freeze.md](../native-backend-protocol-data-contract-freeze.md)銆乕native-companion-old-main-parity-matrix.md](../native-companion-old-main-parity-matrix.md)
> 绾︽潫锛?*蹇呴』涓?P6锛坵indow 鎷撴墤/鍐呭瓨/heartbeat 鎸佷箙鍖栵級鍒嗗紑鑾锋壒锛屼絾涓や釜鎵瑰噯閮芥槸鐢熶骇鎺ョ嚎鐨勫墠鎻?*銆傛湰鐢宠鍙巿鏉冦€岀敓鎴愬崗璁€嶄晶鐨勫彲閫夊瓧娈碉紱P6 鍙﹁鎺堟潈鎶婅繖浜涘瓧娈靛啓鍏ュ悗鍙?job 鎸佷箙鍖栬浇鑽枫€?
## 1. 鑳藉姏缂哄彛锛堟簮鐮佷簨瀹烇級

褰撳墠鐢熶骇鍥炲悎璺緞鏄崟涓€鍐欏叆鑰咃細

```text
鐢ㄦ埛娑堟伅 -> ChatSendCoordinator锛堝敮涓€ user 鍐欏叆鑰咃級
  -> BackgroundTurnPreparationCoordinator -> BackgroundGenerationInput锛堝喕缁擄級
  -> BackgroundGenerationWorker锛堝敮涓€ Provider 璋冪敤 + assistant 鍐欏叆锛?       -> BackgroundGenerationRepository.runGenerationJob()
       -> ChatGenerationRepository.generateReply()
  -> token/session 鏍￠獙 -> assistant 璁板綍
```

Worker 娑堣垂鐨勬槸鍐荤粨 `BackgroundGenerationInput`/`ChatGenerationInput`锛坄contextEvidence` + `sessionPolicy`锛夈€傚鐓х煩闃碉紙搂2 琛?1/2/7/9锛夋槑纭細褰撳墠 Worker **涓?*杩愯鏃?`engine.py` 鐨勬彁绀鸿瘝瑁呴厤锛屼篃**涓?*杩愯鍥炲悎鍚庣殑 master/鍐呭瓨鎶曞奖銆傝繖浜涘睘浜庢湰娆℃嫇鎵戝寲鐨勭洰鏍囪涓猴紝浣嗗簳灞傜敓鎴愬崗璁病鏈夋壙杞藉畠浠殑杞戒綋锛?
- 娌℃湁銆屼笉鍙彉绐楀彛/topology 涓婁笅鏂囥€嶅瓧娈碉紙绐楀彛 id銆乺oot id銆佺埗 id銆乫ork 蹇収 revision銆亀indow kind锛夈€?- 娌℃湁缁撴瀯鍖?`TurnPlan` 杈撳叆锛坱urn 鎰忓浘銆佸姩浣溿€佽鑹层€佺煡璇嗙偣鐨勫綊涓€鍖?`LlmSessionPolicyContext` 涔嬪鐨勭ǔ瀹氬瓧娈碉級銆?- 娌℃湁 `InitiativePlan` 鏉ユ簮瀛楁锛坔eartbeat/initiative 鐢熸垚鐨勫洖鍚堥渶鏍囪鏉ユ簮锛岄伩鍏嶄笌鐢ㄦ埛鍥炲悎娣锋穯锛夈€?- 娌℃湁鍚庣疆鐨勩€佸凡楠岃瘉鐨?`StructuredTurnOutcome` 杈撳嚭锛堝洖鍚堝綊涓€鍖栬瘎浠枫€佸綊涓€鍖栧姩浣溿€乵aster 璇佹嵁銆佺獥鍙ｅ綊灞炪€佸鐞嗚繃绋嬫憳瑕侊級銆?
`idempotent per job` 闇€瑕佹妸浠ヤ笂蹇収鍦?job 鐢熸垚鏃跺浐鍖栵紝涓旈噸璇曚笉寰楄鍙栨洿鏂扮殑鍐呭瓨锛汸3 瀹氫箟鐢熸垚渚у瓧娈典笌瑙ｆ瀽椤哄簭锛孭6 瀹氫箟 job 琛ㄨ浇鑽峰拰 migration銆備簩鑰呭潎鏈壒鍑嗗墠锛屼笉鑳藉０绉拌繖浜涘揩鐓у湪鐢熶骇 Worker 涓彲鎭㈠銆?
## 2. 鏈€灏忔彁妗堬紙寰呭鎵瑰悗鍐嶇粏鍖栵級

鍦ㄥ喕缁撳眰鏂板**鍚戝悗鍏煎鐨勩€佸彲閫夛紙榛樿 null锛?*瀛楁锛涗笉鏀瑰彉浠讳綍鏃㈡湁蹇呭～瀛楁涓庣幇鏈夎涓恒€傝惤浣嶏細

```text
core/llm  (LlmGenerationLifecycle.kt)
  - 鍙€?immutable window/topology context锛堢ǔ瀹?wire 瀛楃涓?+ revision锛屾棤 UI/DTO锛?  - 鍙€?TurnPlan锛堝姩浣?瑙掕壊/鐭ヨ瘑鐐?闅惧害绛夊綊涓€鍖栧揩鐓э級
  - 鍙€?InitiativePlan source锛堟灇涓?鏍囧織锛屾爣璁?initiative 鐢熸垚鏉ユ簮锛?  - 鍙€?StructuredTurnOutcome锛坧ost-turn 鎶曞奖杈撳嚭锛岀粡鏍￠獙锛?
core/data/llm  (ChatGenerationInput)
  - 杞彂涓婅堪鍙€夊瓧娈碉紙榛樿 null锛?
core/data/background  (BackgroundGenerationInput / runGenerationJob)
  - 杞彂涓婅堪鍙€夊瓧娈碉紱瀹為檯 job 杞借嵎鍥哄寲銆佹棫 job 璇诲彇涓?migration 鐢?P6 鎵瑰噯鍚庡疄鏂?```

涓嶆秹鍙婏細
- `core/model`銆乣core/protocol`锛堟棤鏋氫妇/鍗忚 schema 鏀瑰姩锛夈€?- Room entity銆丏AO銆乻chema銆乵igration銆佸悗鍙?job 琛ㄨ浇鑽枫€?- `SecretStore`銆佸鍏?瀵煎嚭銆佸悗鍙?job 鎸佷箙鍖栬浇鑽疯〃缁撴瀯锛坕dempotency 鐩稿叧鎸佷箙鍖栫敱 P6 鍗曠嫭鑾锋壒锛夈€?
### 2.1 杞借嵎杈圭晫

- `TurnPlan`锛氭湁闄愰暱搴︺€佺櫧鍚嶅崟鍖?wire 瀛楃涓诧紙鍔ㄤ綔鈭坰tudy/goal/companion 鐧藉悕鍗曪紝瑙掕壊鈭坄StudentRoleWire.ALL`锛夛紝闅惧害 clamp `0f..1f`锛岀煡璇?閿欒/杩锋€濇湁瀛楃涓婇檺锛屽鐢?`SessionTurnContracts` 鐨?bounded 褰掍竴鍖栥€?- `StructuredTurnOutcome`锛氬彧鍚綊涓€鍖栬瘎浠凤紙correctness/depth clamp `0f..1f`锛夈€佸綊涓€鍖栧姩浣溿€乵aster 璇佹嵁锛坄none`/passed/partial/failed锛夈€佺獥鍙ｅ綊灞炰笌澶勭悊鎽樿锛?*缁濅笉鎼哄甫鍘熷 Provider 鏂囨湰鎴栧師濮嬬敤鎴锋枃鏈?*銆?- 涓婁笅鏂?topology 涓婁笅鏂囷細`windowId`/`rootId`/`parentId?`/`WindowKind` wire 鍊?+ 涓嶅彲鍙?`forkRevision`锛岀函瀛楃涓?Long锛屾棤 Entity/DAO銆?
### 2.2 鍚戝悗璇诲彇琛屼负

- 鎵€鏈夋柊瀛楁榛樿 `null`銆傛棫 job / 鏃ц皟鐢ㄦ柟缂虹渷瀛楁鏃惰涓轰笌浠婂ぉ瀹屽叏涓€鑷达細planner 涓嶆敞鍏ユ柊鍧楋紝outcome 缂虹渷涓哄畨鍏?no-op锛坄StructuredTurnOutcome.empty`锛夈€?- 鏃犲瓧娈佃閲嶅懡鍚嶄负蹇呭～锛涙棤鍗忚 schema 椤跺眰瀛楁鏂板锛堣繖鏄敓鎴愬崗璁唴閮ㄨ浇鑽凤紝涓嶈繘鍏?`VersionedProtocolSchemas.all`锛夈€?
### 2.3 澶辫触鏄犲皠

- 浠讳綍鐣稿舰/鏈煡鐨勬柊瀛楁鍊?鈫?瀹夊叏 no-op锛氬綊涓€鍖栦负榛樿鍊兼垨绌?outcome锛岀粷涓嶅弽搴忓垪鍖栧嚭 raw Provider 鏂囨湰銆?- Provider 杈撳嚭涓嶈兘瑙ｆ瀽涓哄彲閫夌粨鏋勫寲 envelope 鈫?浠嶆寜鏃㈡湁 plain-reply 琛屼负鍐欏叆 assistant锛沗StructuredTurnOutcome` 鍙栧畨鍏ㄧ┖鍊硷紝涓嶆墽琛?post-turn 鎶曞奖锛屼篃涓嶉€忎紶 Provider 閿欒璇︽儏銆?- Provider 杈撳嚭鑳借В鏋?envelope銆佷絾 outcome 瀛愬璞℃牎楠屽け璐?鈫?淇濈暀宸查獙璇佺殑 `reply`锛宱utcome 鍙栧畨鍏ㄧ┖鍊硷紱涓嶅洜杈呭姪鏁欏鍏冩暟鎹け璐ヤ涪寮冪敤鎴峰彲鐢ㄥ洖澶嶃€?
### 2.4 鏃?raw transcript 閲嶅

- 鏂板瓧娈典腑鏃?`raw transcript`銆佹棤 `raw message text`銆佹棤 Authorization/Bearer/URL/瀵嗛挜銆傛墍鏈夋枃鏈粡 `sanitizeContractText` 涓庣櫧鍚嶅崟褰掍竴鍖栥€?- `StructuredTurnOutcome` 涓嶅鍒剁敤鎴?assistant 鍘熷鍐呭锛涘彧鎵胯浇褰掍竴鍖栧€笺€?
## 3. 鍐荤粨鑼冨洿涓庡奖鍝?
| 鍖哄煙 | 鍙兘鍙樻洿 | 褰撳墠鐘舵€?|
|---|---|---|
| `core/llm` | `LlmGenerationLifecycle.kt` 鍙€夊瓧娈?+ planner 娉ㄥ叆 + outcome 鏍￠獙 | 鏈壒鍑?|
| `core/data/llm` | `ChatGenerationInput` 杞彂鍙€夊瓧娈?| 鏈壒鍑?|
| `core/data/background` | `BackgroundGenerationInput`/`runGenerationJob` 蹇収鍥哄寲 | 鏈壒鍑?|
| `core/model` | 鏃?| 涓嶈Е鍙?|
| `core/protocol` | 鏃?| 涓嶈Е鍙?|
| `core/data/local` | 鏃狅紙idempotency 鎸佷箙鍖栧綊 P6锛?| 涓嶈Е鍙?|
| `SecretStore` | 鏃?| 涓嶈Е鍙?|

## 4. 鍏煎銆佹祴璇曚笌鍥炴粴

### 4.1 缁撴瀯鍖栫粨鏋滅殑鏉ユ簮涓庢椂搴?
`StructuredTurnOutcome` 涓嶆槸鐢?Worker 鐚滄祴銆佷篃涓嶆槸浠庢渶鏂?assistant 璁板綍鍥炴煡銆傝幏鎵瑰疄鐜板繀椤诲湪 `ChatGenerationRepository` 鎸佹湁 Provider 鍘熷杈撳嚭鐨勭煭鏆傛墽琛岀獥鍙ｅ唴鎸変互涓嬮『搴忓伐浣滐細

```text
Provider raw output (ephemeral)
  -> optional AssistantTurnEnvelope parser
  -> valid reply + validated StructuredTurnOutcome, or plain reply + empty outcome
  -> existing assistant persistence
  -> BackgroundGenerationOutcome(assistantMessageId, optional outcome)
  -> P6-approved PostTurnProjector keyed by job id
```

`AssistantTurnEnvelope` 鐨?wire 褰㈡€併€佸瓧娈电櫧鍚嶅崟涓庢渶澶ч暱搴﹀繀椤诲湪杩涘叆瀹炵幇鍓嶅啓鍏ユ鐢宠鐨勬壒鍑嗛檮褰曪紱涓嶅緱鎶婃湭瑙ｆ瀽 raw Provider 杈撳嚭淇濆瓨鍒?outcome銆乯ob snapshot 鎴栬瘖鏂〃銆傝В鏋愬け璐ユ槸鍏煎鍥為€€锛屼笉鏄?assistant 鍐欏叆澶辫触銆?
鎵瑰噯闄勫綍鐨?v1 envelope 鍥哄畾涓哄崟涓?JSON object锛?
```json
{
  "version": "v1",
  "reply": "鐢ㄦ埛鍙鐨?assistant 姝ｆ枃",
  "outcome": {
    "correctness": 0.0,
    "depth": 0.0,
    "actionType": "ask",
    "studentRole": "probing_student",
    "knowledgePoint": "",
    "evidenceType": "none",
    "evidenceStatus": "none",
    "scopeCategory": "",
    "scopeCount": 0,
    "processSummary": ""
  }
}
```

`reply` 鏄敮涓€浼氬啓鍏ユ棦鏈?assistant 娑堟伅璁板綍鐨勫彲瑙佹枃鏈紱`outcome` 鍙帴鍙楃櫧鍚嶅崟瀛楁銆佸凡鏈夐檺鍊间笌 bounded 鏂囨湰銆俙version` 闈?`v1`銆佺己澶?`reply`銆侀潪瀵硅薄 JSON 鎴栦换浣?outcome 瀛楁鏍￠獙澶辫触锛岄兘璧?plain-reply + empty-outcome 鍏煎璺緞銆傞涓?P3 瀹炵幇涓嶅厑璁?outcome 鐩存帴鎼哄甫 `MemoryObservation` 鎴栧師濮嬭瘉鎹鏂囷紱杩欑被鍚庣疆鎶曞奖鍙秷璐瑰凡鎵瑰噯鐨勫綊涓€鍖栧瓧娈靛拰 P6 provenance handle銆?
### 娴嬭瘯璁″垝

- 搴忓垪鍖?鍏煎锛氱己鐪佹柊瀛楁鐨勬棫 job 鍙銆佽涓轰笉鍙橈紱娌℃湁 structured envelope 鐨?Provider 鍥炲浠嶆寜 plain reply 鍐欏叆銆?- 蹇収鍥哄寲锛氭柊 job 璺ㄩ噸鍚繚鐣欎笉鍙彉绐楀彛蹇収锛涢噸璇曚笉璇诲彇鏇存柊鐨勫唴瀛樸€?- 鏍￠獙澶辫触锛氱暩褰㈢粨鏋勫寲杈撳嚭鏄犲皠涓哄畨鍏ㄧ┖ outcome锛涙甯?assistant 鍥炲淇濈暀锛屼笉閫忎紶 Provider 璇︽儏銆?- 鍗曞啓鍏ヨ€呬笉鐮村潖锛歚BackgroundGenerationWorker` 浠嶅彧璋?`BackgroundGenerationRepository.runGenerationJob`锛宍PostTurnProjector` 浠呭湪鏍￠獙閫氳繃鍚庢寜 job id 骞傜瓑鎶曞奖銆?- 鍥炲綊锛歚core:llm` / `core:data` / `app` JVM 娴嬭瘯鍏ㄧ豢锛涗笉寮曞叆鐪熷疄 Provider 璋冪敤銆佷笉璇诲彇鐢熶骇 API key銆?
### 鍥炴粴

- 瀛楁鍙€夈€侀粯璁?null銆侾3 鍗曠嫭鍥炴粴鏃朵繚鎸佸瓧娈佃鍙栧吋瀹癸紱鐢熶骇 job 杞借嵎鐨?schema 鐢熷懡鍛ㄦ湡鐢?P6 绠＄悊锛屼笉鑳介€氳繃闄嶄綆 Room 鐗堟湰鍥炴粴銆?- 鑻ュ凡鍥哄寲 job 蹇収鍚柊瀛楁锛岄檷绾ц鍙栫瓥鐣ワ細缂虹渷瀛楁鏃舵寜鏃ц涓鸿繍琛岋紝蹇界暐鏈煡鍙€夊瓧娈点€?
## 5. 瀹℃壒闂ㄧ

- [ ] 鐢ㄦ埛鎵瑰噯璇?P3 鐢熸垚鍗忚鍙樻洿鏂瑰悜锛堟湰鐢宠锛?- [ ] 涓?P6 鐢宠鍒嗗紑鎵瑰噯锛堟湰鐢宠涓嶅惈 Room/DAO/migration/job 琛ㄥ彉鏇达級
- [ ] 瀹℃壒鍚庯細鍗曠嫭鎻愪氦瀹屾暣鍙樻洿璇存槑锛堝彈褰卞搷鏂囦欢銆佸崗璁瓧娈点€佸吋瀹广€佹祴璇曘€佸洖婊氾級鍚庯紝鎵嶈繘鍏?Task 11 瀹炵幇

鍦ㄤ笂杩板嬀閫夊畬鎴愬墠锛宍BackgroundGenerationWorker`/`ChatGenerationRepository` 淇濇寔鐜版湁鍙€夊瓧娈佃涓轰笉鍙樸€?
## 6. 璁″垝鑷锛堟湰鐢宠鏄惁瀹屽叏鍐荤粨澶栬涓猴級

鏈敵璇蜂笉鍖呭惈锛歚WindowConversationContract`锛坒eature:chat锛岄潪鍐荤粨锛夈€乣WindowTopologyPolicy`/`CompanionMemoryEvolutionPolicy`/`LearningScopeGuard`/`InitiativeEligibilityPolicy`锛坈ore:domain锛岄潪鍐荤粨锛夈€乣WindowConversationAssembly`锛坅pp wiring锛岄潪鍐荤粨锛夈€傝繖浜涘湪 Package A/B 宸插疄鐜颁笖鍚勮嚜鏈夋祴璇曪紱鏈敵璇峰彧涓烘妸瀹冧滑钀藉埌鐪熷疄 Worker 鐢熸垚鍗忚鎻愪緵鍙寔涔呭寲鐨勬壙杞姐€?
---

## 闄勫綍 A锛欻eartbeat / Initiative 鍚庡彴鐢熸垚璇箟锛堟湰闄勫綍闇€鍗曠嫭鑾锋壒鍚庢墠鑳藉疄鐜帮級

> 鏈枃妗ｄ富浣撶幇宸茶幏鎵广€備互涓嬪瓧娈?璇箟灞炰簬 heartbeat/initiative 鐢熸垚璺緞锛岃嫢姝ら檮褰曟湭鑾锋壒鍑嗭紝**涓嶅緱**瀹炵幇瀵瑰簲鐨勭敓浜?dispatch/planner 鏀瑰姩锛屼篃涓嶅緱鏀瑰姩 `core/data/background` 涓?`core/llm` 鐩稿叧浠ｇ爜銆?
### A.1 闇€瑕佹槑纭殑瀛楁

- **job kind**锛氭柊澧炴槑纭殑 initiative 鍚庡彴浠诲姟 kind锛堝尯鍒簬鏅€氱敤鎴峰洖鍚堢殑 `Generation`锛屼緥濡?`Generation` + `destination=Heartbeat|Initiative` 鎴栫嫭绔?kind 鏍囧織锛夛紝鐢ㄤ簬鍖哄垎鎸佷箙鍖栦换鍔＄被鍨嬶紱涓嶅緱澶嶇敤鏅€氳亰澶╃殑瀛楁璇箟銆?- **`spaceId`**锛氬緟鍐欏叆绐楀彛鎵€灞炵┖闂达紝鍙栬嚜 `InitiativeEligibilityInput.spaceId`锛堜笉瀛樺湪鏃朵笉鍐欏叆锛岀粷涓嶆妸 `targetWindowId` 褰撲綔 `spaceId`锛夈€?- **`targetWindowId`**锛氭伆濂界瓑浜庤鏈轰細涓殑 `input.window.id`锛堟牴鎴栨樉寮忓惎鐢ㄧ殑瀛愮獥锛夛紝姘镐笉鎸囧悜鐖躲€佸厔寮熸垨鏍逛箣澶栥€?- **`InitiativePlan`**锛氫綔涓虹粨鏋勫寲鏈轰細蹇収鎼哄甫锛坕ntent / topologyNodes / evidenceHandles / toneConstraints / expiry / minCooldown / deliveryPolicy锛夛紱棣栫増涓嶆妸璁″垝钀戒负鍥哄畾鎻愰啋瀛楃涓层€?- **`LlmWindowContext` / `LlmTurnPlan`**锛氶殢 `InitiativePlan` 涓€璧蜂綔涓轰笉鍙彉 envelope 杩?job 蹇収锛坄windowId/rootId/parentId?/windowKind/forkRevision`銆乣intent/actionType/studentRole/knowledgePoint/difficulty/studyMethod/toneConstraints/expiry/minCooldown`锛夈€?
### A.2 鏃犵敤鎴锋秷鎭椂鐨勭敓鎴愯涔?
- 涓€涓?initiative/heartbeat job **涓嶆惡甯?`userMessageId`锛屼篃涓嶆惡甯︾敤鎴疯緭鍏ユ枃鏈?*銆?- 鐢熸垚鐢?`InitiativePlan` + envelope 椹卞姩锛氬綋 `assistantTurnEnvelope.turnPlan` 瀛樺湪涓?`userText` 涓虹┖鏃讹紝planner 鍏佽鍋氳鍒掗┍鍔ㄧ殑寮€鍦虹敓鎴愶紝**涓嶅啀浠ョ┖鐢ㄦ埛娑堟伅杩斿洖 `BlankPrompt`**锛涜繖鏄湰闄勫綍鏂板鐨?planner 鑳藉姏銆?- **鏄庣‘绂佹**锛氫负鍑戦潪绌?`userText` 浼€犲崰浣?绌轰覆娑堟伅鏉ユā鎷?heartbeat锛堝崰浣?`userText` 涓€寰嬩笉鍏佽锛夈€?- 鎸囦护/寮€鍦哄彲瑙佹枃鏈敱璁″垝鍦?Worker 绔敓鎴愬悗鍐欏叆 assistant 璁板綍锛沗reply` 浠嶆槸鍞竴鍐欏叆鐨?assistant 鍙鏂囨湰銆?
### A.3 浠诲姟缁戝畾涓庡畨鍏?
- 浠诲姟蹇呴』缁戝畾姝ｇ‘鐨?`spaceId` 涓?`targetWindowId`锛沗targetWindowId` 鍐冲畾鍚庡彴浠诲姟褰掑睘涓?job 閿紝涓嶇敤浜庣┖闂磋绠椼€?- 閲嶈瘯鏃跺繀椤讳娇鐢ㄨ job 鎸佷箙鍖栫殑涓嶅彲鍙?initiative 蹇収锛岃€岄潪閲嶆柊璇诲彇褰撳墠鍐呭瓨銆?- Provider 澶辫触 鈫?瀹夊叏澶辫触鐮侊紙bounded銆佹棤 raw Provider 鏂囨湰锛夛紱缁撴瀯鍖栬В鏋愬け璐?鈫?plain-reply + `StructuredTurnOutcome.EMPTY`銆?- 涓嶆柊澧炵浜屾潯 assistant 鍐欏叆璺緞锛沗BackgroundGenerationWorker` 浠嶄负鍞竴 Provider 璋冪敤鑰呬笌 assistant 鍐欏叆鑰呫€?- 涓嶄繚瀛?raw transcript / Provider 鍘熸枃 / URL / Authorization / secret銆?
### A.4 杈圭晫

鏈檮褰曚笉娑夊強锛歊oom entity/DAO/migration/schema锛堝綊 P6 闄勫綍锛夈€乣core:model`/`core:protocol` 鏋氫妇鎴栧崗璁《灞?schema銆乣SecretStore`銆佸鍏ュ鍑恒€佺鍚?PWA/Capacitor銆?
