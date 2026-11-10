<#ftl output_format="XHTML" encoding="UTF-8">
<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>${project.name} 追溯报告</title>
<#include "_style.ftl">
</head>
<body>

<div class="cover">
  <h1>${project.name}<br />需求追溯报告</h1>
  <p>依据：${project.baselineLabel}</p>
  <p>生成时间：${project.generatedAt}</p>
  <p class="muted">由 SysArch Studio 从模型自动生成</p>
</div>

<div class="chapter">
  <h2>1 汇总</h2>
  <table>
    <tr><th>需求总数</th><th>已满足</th><th>已验证</th><th>未满足</th><th>覆盖率（已满足 / 总数）</th></tr>
    <tr><td>${stats.requirementCount}</td><td>${stats.satisfied}</td><td>${stats.verified}</td><td class="missing">${stats.unsatisfied}</td><td>${stats.coverage}</td></tr>
  </table>
  <h2>2 未满足清单</h2>
  <table>
    <tr><th>需求编号</th><th>名称</th><th>层</th><th>优先级</th><th>需求正文</th></tr>
    <#list unsatisfied as r>
    <tr><td class="missing">${r.reqId}</td><td>${r.name}</td><td>${r.layer}</td><td>${r.priority}</td><td>${r.text}</td></tr>
    <#else>
    <tr><td colspan="5" class="ok">全部需求均已满足</td></tr>
    </#list>
  </table>
  <h2>3 追溯明细</h2>
  <table>
    <tr><th>需求编号</th><th>名称</th><th>满足者</th><th>验证者</th><th>验证方法</th><th>状态</th></tr>
    <#list requirements as r>
    <tr><td>${r.reqId}</td><td>${r.name}</td><td><#list r.satisfiedBy as s>${s}<#sep>、</#sep></#list></td><td><#list r.verifiedBy as v>${v}<#sep>、</#sep></#list></td><td>${r.verifyMethod}</td><td class="${r.statusClass}">${r.status}</td></tr>
    </#list>
  </table>
</div>

</body>
</html>
