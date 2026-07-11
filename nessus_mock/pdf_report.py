"""Minimal PDF generation for mock scan exports (no external network)."""

from __future__ import annotations

import io
from datetime import datetime, timezone
from typing import Any

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
from reportlab.lib import colors


def build_scan_pdf(
    scan: dict[str, Any],
    chapters: str | None = None,
    fmt: str = "pdf",
) -> bytes:
    """Return bytes for the requested export format."""
    fmt = (fmt or "pdf").lower()
    if fmt == "csv":
        return _csv(scan)
    if fmt in ("html", "htm"):
        return _html(scan).encode("utf-8")
    if fmt == "nessus":
        return _nessus_xml(scan).encode("utf-8")
    return _pdf(scan, chapters)


def _pdf(scan: dict[str, Any], chapters: str | None) -> bytes:
    buf = io.BytesIO()
    doc = SimpleDocTemplate(buf, pagesize=A4, leftMargin=2 * cm, rightMargin=2 * cm)
    styles = getSampleStyleSheet()
    story = []

    info = scan.get("info") or {}
    story.append(Paragraph("CyberAsk Demo — Vulnerability Report", styles["Title"]))
    story.append(Spacer(1, 0.4 * cm))
    story.append(Paragraph(f"<b>Scan:</b> {info.get('name', scan.get('name', 'Unknown'))}", styles["Normal"]))
    story.append(Paragraph(f"<b>Status:</b> {info.get('status', scan.get('status', 'completed'))}", styles["Normal"]))
    story.append(Paragraph(f"<b>Policy:</b> {info.get('policy', 'Demo Policy')}", styles["Normal"]))
    story.append(Paragraph(f"<b>Scanner:</b> {info.get('scanner_name', 'Demo Scanner')}", styles["Normal"]))
    story.append(
        Paragraph(
            f"<b>Generated:</b> {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}",
            styles["Normal"],
        )
    )
    if chapters:
        story.append(Paragraph(f"<b>Chapters:</b> {chapters}", styles["Normal"]))
    story.append(Spacer(1, 0.6 * cm))
    story.append(
        Paragraph(
            "This is synthetic data for Google Play application review. "
            "It is not a real security assessment.",
            styles["Italic"],
        )
    )
    story.append(Spacer(1, 0.6 * cm))

    story.append(Paragraph("Executive summary", styles["Heading2"]))
    hosts = scan.get("hosts") or []
    vulns = scan.get("vulnerabilities") or []
    story.append(
        Paragraph(
            f"Hosts: {len(hosts)} · Unique findings: {len(vulns)} · "
            f"Remediations: {len((scan.get('remediations') or {}).get('remediations') or [])}",
            styles["Normal"],
        )
    )
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Top findings", styles["Heading2"]))
    rows = [["Severity", "Plugin", "Family", "Count"]]
    for v in sorted(vulns, key=lambda x: -(x.get("severity") or 0))[:25]:
        rows.append(
            [
                str(v.get("severity", "")),
                (v.get("plugin_name") or "")[:48],
                (v.get("plugin_family") or "")[:24],
                str(v.get("count") or 1),
            ]
        )
    table = Table(rows, colWidths=[2 * cm, 8 * cm, 4 * cm, 2 * cm])
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1a365d")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTSIZE", (0, 0), (-1, -1), 8),
                ("GRID", (0, 0), (-1, -1), 0.25, colors.grey),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.whitesmoke, colors.lightgrey]),
            ]
        )
    )
    story.append(table)
    story.append(Spacer(1, 0.6 * cm))

    story.append(Paragraph("Hosts", styles["Heading2"]))
    hrows = [["Host", "Critical", "High", "Medium", "Low", "Info"]]
    for h in hosts[:40]:
        hrows.append(
            [
                h.get("hostname", ""),
                str(h.get("critical", 0)),
                str(h.get("high", 0)),
                str(h.get("medium", 0)),
                str(h.get("low", 0)),
                str(h.get("info", 0)),
            ]
        )
    htable = Table(hrows, colWidths=[6 * cm, 2 * cm, 2 * cm, 2 * cm, 2 * cm, 2 * cm])
    htable.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#2c5282")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTSIZE", (0, 0), (-1, -1), 8),
                ("GRID", (0, 0), (-1, -1), 0.25, colors.grey),
            ]
        )
    )
    story.append(htable)

    doc.build(story)
    return buf.getvalue()


def _csv(scan: dict[str, Any]) -> bytes:
    lines = ["plugin_id,plugin_name,severity,plugin_family,count,cve"]
    for v in scan.get("vulnerabilities") or []:
        lines.append(
            ",".join(
                [
                    str(v.get("plugin_id", "")),
                    f"\"{(v.get('plugin_name') or '').replace(chr(34), '')}\"",
                    str(v.get("severity", "")),
                    f"\"{(v.get('plugin_family') or '').replace(chr(34), '')}\"",
                    str(v.get("count") or 1),
                    f"\"{(v.get('cve') or '').replace(chr(34), '')}\"",
                ]
            )
        )
    return ("\n".join(lines) + "\n").encode("utf-8")


def _html(scan: dict[str, Any]) -> str:
    info = scan.get("info") or {}
    name = info.get("name", scan.get("name", "Scan"))
    items = "".join(
        f"<li>[{v.get('severity')}] {v.get('plugin_name')} (id={v.get('plugin_id')})</li>"
        for v in (scan.get("vulnerabilities") or [])[:50]
    )
    return f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>{name}</title></head>
<body>
<h1>CyberAsk Demo Report</h1>
<p><b>Scan:</b> {name}</p>
<p>Synthetic data for application review only.</p>
<ul>{items}</ul>
</body></html>
"""


def _nessus_xml(scan: dict[str, Any]) -> str:
    info = scan.get("info") or {}
    name = info.get("name", scan.get("name", "Scan"))
    return f"""<?xml version="1.0" ?>
<NessusClientData_v2>
  <Report name="{name}">
    <ReportHost name="demo-host.local">
      <HostProperties>
        <tag name="host-ip">10.0.0.10</tag>
      </HostProperties>
    </ReportHost>
  </Report>
</NessusClientData_v2>
"""
