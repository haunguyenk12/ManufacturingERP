param(
    [string]$DocumentPath = "report\OmniPlant_Capstone_2_System_Report.docx"
)

$ErrorActionPreference = 'Stop'
$resolvedPath = (Resolve-Path -LiteralPath $DocumentPath).Path
$reportDir = Split-Path -Parent $resolvedPath
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupPath = Join-Path $reportDir "OmniPlant_Capstone_2_System_Report.before-chapter3-$timestamp.docx"
$mediaDir = Join-Path $env:TEMP "omniplant-chapter3-media-$timestamp"
$workingPath = Join-Path $mediaDir 'working-report.docx'

New-Item -ItemType Directory -Path $mediaDir -Force | Out-Null

# Preserve the already-inserted architecture, use-case slices, and swimlane slices.
Add-Type -AssemblyName System.IO.Compression
$sourceStream = [System.IO.FileStream]::new(
    $resolvedPath,
    [System.IO.FileMode]::Open,
    [System.IO.FileAccess]::Read,
    [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete
)
$sourceZip = [System.IO.Compression.ZipArchive]::new(
    $sourceStream,
    [System.IO.Compression.ZipArchiveMode]::Read,
    $false
)
try {
    foreach ($imageNumber in 3..10) {
        $entry = $sourceZip.GetEntry("word/media/image$imageNumber.png")
        if (-not $entry) {
            throw "Required embedded image word/media/image$imageNumber.png was not found."
        }
        $target = Join-Path $mediaDir "image$imageNumber.png"
        $input = $entry.Open()
        $output = [System.IO.File]::Create($target)
        try {
            $input.CopyTo($output)
        }
        finally {
            $output.Dispose()
            $input.Dispose()
        }
    }
}
finally {
    $sourceZip.Dispose()
    $sourceStream.Dispose()
}

[System.IO.File]::Copy($resolvedPath, $backupPath, $false)
[System.IO.File]::Copy($resolvedPath, $workingPath, $true)

$word = $null
$doc = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $doc = $word.Documents.Open($workingPath)

    $chapterFind = $doc.Content.Duplicate
    $chapterFind.Find.ClearFormatting()
    $chapterFind.Find.Text = 'SYSTEM ARCHITECTURE, ANALYSIS, DESIGN AND IMPLEMENTATION'
    $chapterFind.Find.Style = $doc.Styles.Item('Heading 1')
    $chapterFound = $chapterFind.Find.Execute()
    $nextFind = $doc.Content.Duplicate
    $nextFind.Start = $chapterFind.End
    $nextFind.Find.ClearFormatting()
    $nextFind.Find.Text = 'RESULTS AND DISCUSSION'
    $nextFind.Find.Style = $doc.Styles.Item('Heading 1')
    $nextFound = $nextFind.Find.Execute()

    if (-not $chapterFound -or -not $nextFound) {
        throw 'Could not locate the Chapter 3 boundaries.'
    }

    $chapterStart = $chapterFind.Paragraphs.Item(1).Range.Start
    $nextChapterStart = $nextFind.Paragraphs.Item(1).Range.Start

    $replaceRange = $doc.Range($chapterStart, $nextChapterStart)
    $replaceRange.Delete() | Out-Null
    $script:insertPos = $chapterStart
    $script:doc = $doc

    function Set-ParagraphStyle {
        param($Paragraph, [string]$StyleName)
        $Paragraph.Range.Style = $script:doc.Styles.Item($StyleName)
    }

    function Add-TextParagraph {
        param(
            [string]$Text,
            [string]$StyleName = 'Normal',
            [int]$Alignment = 0,
            [switch]$Bold,
            [int]$HighlightColor = 0
        )
        $start = $script:insertPos
        $range = $script:doc.Range($start, $start)
        $range.Text = $Text + [char]13
        $paragraph = $script:doc.Range($start, $start + $Text.Length + 1).Paragraphs.Item(1)
        Set-ParagraphStyle $paragraph $StyleName
        $paragraph.Alignment = $Alignment
        if ($Bold) { $paragraph.Range.Bold = 1 }
        if ($HighlightColor -ne 0) { $paragraph.Range.HighlightColorIndex = $HighlightColor }
        $script:insertPos = $paragraph.Range.End
    }

    function Add-FigureImage {
        param([string]$Path)
        $start = $script:insertPos
        $paragraphRange = $script:doc.Range($start, $start)
        # A standalone [char] value is coerced to "13" by Word COM. Use an
        # explicit string carriage return so this becomes a real paragraph.
        $paragraphRange.Text = "`r"
        $paragraph = $script:doc.Range($start, $start + 1).Paragraphs.Item(1)
        Set-ParagraphStyle $paragraph 'Normal'
        $paragraph.Alignment = 1
        $anchor = $script:doc.Range($start, $start)
        $shape = $script:doc.InlineShapes.AddPicture($Path, $false, $true, $anchor)
        $shape.LockAspectRatio = -1
        # Reacquire the paragraph after adding the inline shape. The original
        # COM range can retain its pre-image end position and make later text
        # drift backward into a preceding table.
        $paragraph = $script:doc.Range($start, $start).Paragraphs.Item(1)
        $script:insertPos = $paragraph.Range.End
    }

    function Add-SequenceCaption {
        param(
            [ValidateSet('Figure', 'Table')][string]$Label,
            [string]$Description
        )
        $start = $script:insertPos
        # Reserve an isolated caption paragraph before inserting the SEQ field.
        # Without this paragraph, Word can attach the field to the following
        # chapter heading and corrupt both its text and style.
        $paragraphRange = $script:doc.Range($start, $start)
        $paragraphRange.Text = "`r"
        $prefix = $script:doc.Range($start, $start)
        $prefix.Text = "$Label "
        $fieldStart = $start + $Label.Length + 1
        $fieldRange = $script:doc.Range($fieldStart, $fieldStart)
        $field = $script:doc.Fields.Add($fieldRange, -1, "SEQ $Label \* ARABIC", $true)
        # Result.End is immediately before Word's hidden field-end marker.
        # Move one character forward so the description remains outside the
        # field and is not erased when numbering is refreshed.
        $tailStart = $field.Result.End + 1
        $tail = $script:doc.Range($tailStart, $tailStart)
        $tail.Text = ". $Description"
        $paragraph = $script:doc.Range($start, $start).Paragraphs.Item(1)
        Set-ParagraphStyle $paragraph 'Caption'
        $paragraph.Alignment = 1
        $script:insertPos = $paragraph.Range.End
    }

    function Add-Placeholder {
        param([string]$Text)
        Add-TextParagraph -Text "[[ INSERT FIGURE HERE: $Text -- LANDSCAPE PAGE RECOMMENDED ]]" -StyleName 'Normal' -Alignment 1 -Bold -HighlightColor 7
    }

    function Add-ReportTable {
        param([object[]]$Rows)
        $rowCount = $Rows.Count
        $columnCount = $Rows[0].Count
        $tableRange = $script:doc.Range($script:insertPos, $script:insertPos)
        $table = $script:doc.Tables.Add($tableRange, $rowCount, $columnCount)
        $table.Style = 'Table Grid'
        $table.AllowAutoFit = $true
        for ($row = 0; $row -lt $rowCount; $row++) {
            for ($column = 0; $column -lt $columnCount; $column++) {
                $cellRange = $table.Cell($row + 1, $column + 1).Range
                $cellRange.Text = [string]$Rows[$row][$column]
                $cellRange.ParagraphFormat.Alignment = 0
                $cellRange.Style = $script:doc.Styles.Item('Normal')
            }
        }
        $table.Rows.Item(1).Range.Bold = 1
        $table.AutoFitBehavior(2)
        # Word treats a table's final cell marker as an end-of-row boundary.
        # InsertParagraphAfter creates a genuine body paragraph after the table,
        # so subsequent headings and figures cannot accidentally land in a cell.
        $table.Range.InsertParagraphAfter()
        $script:insertPos = $table.Range.End
    }

    # Chapter title and overview
    Add-TextParagraph 'SYSTEM ARCHITECTURE, ANALYSIS, DESIGN AND IMPLEMENTATION' 'Heading 1'
    Add-TextParagraph 'Chapter Overview' 'Heading 2'
    Add-TextParagraph 'This chapter presents the analysis, architectural design, business-process design, database structure, and implementation principles of OmniPlant. The chapter begins with the overall technical architecture and actor model before describing how the main actors interact with the system. It then explains the end-to-end operational workflow through a swimlane activity diagram.'
    Add-TextParagraph 'The database design is divided into six major functional modules. This modular presentation makes the data model easier to understand while preserving the relationships among administration, master data, sales and planning, purchasing, inventory, and manufacturing execution. The final sections describe the core business rules, API and security design, frontend interaction principles, and deployment environment.'

    # Architecture
    Add-TextParagraph 'System Architecture' 'Heading 2'
    Add-TextParagraph 'Architectural Style' 'Heading 3'
    Add-TextParagraph 'OmniPlant is implemented as a web-based Manufacturing ERP system using a modular-monolith architecture. This approach maintains a single deployable backend while separating the application into clear functional modules. It provides simpler deployment and transaction management than a distributed microservice architecture while still preserving domain boundaries.'
    Add-TextParagraph 'The frontend is responsible for user interaction, client-side validation, permission-aware navigation, query state, internationalization, and responsive presentation. The backend remains the authoritative source for authentication, authorization, business-state transitions, inventory effects, material planning calculations, approval rules, and database consistency.'
    Add-TextParagraph 'PostgreSQL stores transactional and master data, while Redis supports session and token-management functions. Communication between the frontend and backend is performed through RESTful API endpoints under the /api/v1 namespace.'
    Add-FigureImage (Join-Path $mediaDir 'image3.png')
    Add-SequenceCaption 'Figure' 'OmniPlant system architecture and major technical components.'
    Add-TextParagraph 'The architecture separates presentation concerns from business authority. Although the frontend prevents invalid actions where possible, all critical rules are revalidated by the backend. This includes Plant and Warehouse access, Work Order release readiness, material availability, approval permissions, quality disposition, and idempotent inventory transactions.'

    Add-TextParagraph 'Technology Stack' 'Heading 3'
    Add-TextParagraph 'The major technologies and their responsibilities are summarized in the following table.'
    Add-SequenceCaption 'Table' 'Technology stack.'
    Add-ReportTable @(
        @('Layer', 'Technology and responsibility'),
        @('Frontend', 'Next.js App Router, React, and strict TypeScript.'),
        @('UI and styling', 'Tailwind CSS, customized Shadcn UI, and Lucide icons.'),
        @('Client state', 'React Query for server state and Context API for authentication and selected Plant.'),
        @('Internationalization', 'next-intl with English and Vietnamese namespaces.'),
        @('Backend', 'Java 17, Spring Boot, Spring Security, and JPA/Hibernate.'),
        @('Persistence', 'PostgreSQL with Flyway migrations and Redis for session support.'),
        @('Integration', 'RESTful /api/v1 endpoints, UUID identifiers, and a standard response envelope.')
    )

    # Actors and use cases
    Add-TextParagraph 'Functional Analysis and Actor Model' 'Heading 2'
    Add-TextParagraph 'System Actors' 'Heading 3'
    Add-TextParagraph 'OmniPlant defines three primary human actors: Administrator, Manager, and Operator. The actors are separated according to responsibility and authority rather than only by screen visibility.'
    Add-SequenceCaption 'Table' 'Actor responsibilities.'
    Add-ReportTable @(
        @('Actor', 'Main responsibilities'),
        @('Administrator', 'Maintains users, roles, permissions, access scopes, organizational structure, and system-level master data. Reviews audit information and controls access assignments.'),
        @('Manager', 'Manages Sales Orders, material planning, supply decisions, Work Orders, purchasing documents, production approvals, quality disposition, and operational monitoring.'),
        @('Operator', 'Performs authorized warehouse and production activities, including reservation, material issue, production reporting, goods receipt, and Production Receipt submission.'),
        @('System', 'Performs authentication, authorization, MRP calculation, BOM explosion, inventory updates, status transitions, variance calculation, and audit logging.')
    )
    Add-TextParagraph 'The System is not a human actor. It represents automated application behavior triggered by the actions of authenticated users.'

    Add-TextParagraph 'Use Case Model' 'Heading 3'
    Add-TextParagraph 'The following use case diagram presents the functional boundary of OmniPlant and the interactions between its three human actors and the major system functions.'
    foreach ($imageNumber in 4..6) {
        Add-FigureImage (Join-Path $mediaDir "image$imageNumber.png")
    }
    Add-SequenceCaption 'Figure' 'Use case diagram of OmniPlant actors and major system functions.'
    Add-TextParagraph 'The use case diagram describes functional responsibility but does not represent execution order. Administrator use cases focus on system configuration, organizational data, access control, and auditability. Manager use cases cover planning, approval, production control, purchasing, and quality decisions. Operator use cases cover the direct execution of inventory, receiving, and manufacturing activities.'
    Add-TextParagraph 'Several use cases are shared between actors but are controlled by different permissions. For example, both Managers and Operators may view Work Orders, while only authorized Managers can create or release them. Operators may create and submit Production Receipts, but they cannot approve their own receipts or perform final quality disposition. This separation of duties prevents the same user from executing and approving an integrity-sensitive transaction.'

    # Swimlane and workflow
    Add-TextParagraph 'End-to-End Business Process Design' 'Heading 2'
    Add-TextParagraph 'The operational behavior of OmniPlant is document-driven. A user cannot move directly from demand to inventory output without passing through the required planning, production, approval, and quality-control documents. The following workflow is divided into four swimlanes: Administrator, Manager, Operator, and System.'
    foreach ($imageNumber in 7..10) {
        Add-FigureImage (Join-Path $mediaDir "image$imageNumber.png")
    }
    Add-SequenceCaption 'Figure' 'Swimlane activity diagram of the end-to-end OmniPlant business process.'
    Add-TextParagraph 'The swimlane diagram identifies both execution sequence and responsibility. Human decisions are assigned to the role that owns the business outcome, while validation, calculation, inventory posting, and audit recording are placed in the System lane. The diagram also demonstrates that the Administrator prepares the operating environment but does not participate directly in daily purchasing or production execution.'

    Add-TextParagraph 'System Setup and Access Control' 'Heading 3'
    Add-TextParagraph 'The process begins with authentication and authorization. The System validates the user credentials and loads the corresponding roles, permissions, and access scopes. If authentication fails, the request is rejected without entering the business workflow.'
    Add-TextParagraph 'Before operational activities begin, the Administrator configures the Company, Plants, Warehouses, users, roles, permissions, and access scopes. Foundational master data such as units of measure, Items, Bills of Materials, suppliers, Routings, Work Centers, shifts, and work calendars must also be available according to the relevant module permissions.'
    Add-TextParagraph 'The System validates important master-data invariants. Examples include preventing circular BOM structures, requiring an active BOM for material explosion, and ensuring that production master data belongs to the correct Company or Plant context.'

    Add-TextParagraph 'Sales and Material Planning' 'Heading 3'
    Add-TextParagraph 'A Manager creates and confirms a Sales Order or records a manual or forecast demand. Confirmation converts the commercial requirement into an eligible planning demand. Draft Sales Orders do not participate in MRP.'
    Add-TextParagraph 'The Manager selects eligible demand and starts an MRP run. The System creates an immutable snapshot of the selected demand, explodes the active multi-level BOM, evaluates eligible inventory and scheduled supply, and calculates the net requirement.'
    Add-TextParagraph 'netRequirement = max(0, grossRequirement + stockTarget - projectedAvailable)' 'Formula' 1
    Add-TextParagraph 'If sufficient supply exists, the requirement is marked as covered. Otherwise, the System creates a supply suggestion. A suggestion may recommend purchasing the material or manufacturing it internally. The Manager reviews the suggestion, its exception state, and any missing BOM or Routing information before approving, rejecting, or converting it.'

    Add-TextParagraph 'Purchasing Flow' 'Heading 3'
    Add-TextParagraph 'For a BUY requirement, an approved supply suggestion may be converted into a Purchase Requisition. The Manager reviews and approves the requisition before converting it into a Purchase Order and sending the order to the selected supplier.'
    Add-TextParagraph 'When the supplier delivers the material, the Operator posts a Goods Receipt. The System validates the Purchase Order line, Warehouse, received quantity, lot information, and idempotency key. A valid receipt creates an inventory RECEIVE movement, increases raw-material stock, and updates the received quantity and status of the Purchase Order.'

    Add-TextParagraph 'Manufacturing Execution Flow' 'Heading 3'
    Add-TextParagraph 'For a MAKE requirement, an approved suggestion is converted into a Work Order. The Work Order stores the selected product, planned quantity, output Warehouse, BOM evidence, Routing evidence, and demand allocations.'
    Add-TextParagraph 'Materials are reserved before release. Reservation reduces available quantity but does not change physical on-hand stock. If the reservation does not cover all component requirements, the System keeps the Work Order in the BLOCKED state and reports the missing quantities. After sufficient material is reserved, the Manager releases the Work Order.'
    Add-TextParagraph 'The Operator posts a Material Issue for eligible reserved components. The System creates an inventory ISSUE movement and decreases the corresponding stock balance. Issues above the Work Order BOM requirement require an explicit reason and an authorized override.'
    Add-TextParagraph 'During production, the Operator records good, scrap, and rework quantities. These transactions update the Work Order actual quantities and preserve WIP evidence. OmniPlant records these results manually and does not claim automated MES or machine-data integration.'

    Add-TextParagraph 'Receipt, Quality, and Inventory Update' 'Heading 3'
    Add-TextParagraph 'After production output is reported, the Operator creates and submits a Production Receipt. Submission moves the receipt into the PENDING_APPROVAL state without immediately changing inventory.'
    Add-TextParagraph 'An authorized Manager may approve or reject the receipt. Rejection requires a reason and has no stock effect. Approval creates a finished-goods RECEIVE movement and places the received output in HOLD status.'
    Add-TextParagraph 'Quality disposition is performed separately from receipt approval. An AVAILABLE result releases the output for planning and Sales Order fulfillment. A REJECTED result keeps the quantity unavailable for operational use. The System then updates inventory balances, Sales Order fulfillment, operational dashboards, variance information, and audit logs.'

    # Database design and six ERDs
    Add-TextParagraph 'Database Design' 'Heading 2'
    Add-TextParagraph 'Data Modeling Principles' 'Heading 3'
    Add-TextParagraph 'The OmniPlant database uses UUID primary keys and explicit foreign keys for physical relationships. Transactional tables retain timestamps, actor identifiers, and version fields where required for auditability and optimistic concurrency.'
    Add-TextParagraph 'The model separates reusable master data, lifecycle-controlled business documents, and append-only ledger or execution evidence. Some fields, such as reference_id, are intentionally polymorphic and therefore do not have a physical foreign key. These fields are documented as logical relationships rather than being presented as database-enforced relationships.'
    Add-TextParagraph 'Each ERD is independently readable. A table marked as an external reference contains only the relationship key required by the module diagram; its complete definition is presented in the module that owns the table.'

    Add-TextParagraph 'System Administration and Integration ERD' 'Heading 3'
    Add-TextParagraph 'This module defines the organizational and authorization foundation of OmniPlant. It includes users, Companies, Plants, Warehouses, roles, permissions, access scopes, assignments, audit logs, and data-import records.'
    Add-Placeholder 'SYSTEM ADMINISTRATION AND INTEGRATION ERD'
    Add-SequenceCaption 'Figure' 'ERD of the System Administration and Integration module.'
    Add-TextParagraph 'A Company contains multiple Plants, and each Plant contains multiple Warehouses. Roles are connected to permissions through role_permissions. A user receives operational authority through user_role_assignments, which connects a user, role, and access scope.'
    Add-TextParagraph 'The audit structure separates the main audit event from its field-level changes. Data import belongs to this module because it supports controlled integration rather than representing a standalone manufacturing process.'

    Add-TextParagraph 'Product and Production Master Data ERD' 'Heading 3'
    Add-TextParagraph 'This module contains the reusable definitions required before planning or production can begin. It includes units of measure, Items, Bills of Materials, Routings, Routing operations, Work Centers, shifts, work calendars, and standard costs.'
    Add-Placeholder 'PRODUCT AND PRODUCTION MASTER DATA ERD'
    Add-SequenceCaption 'Figure' 'ERD of the Product and Production Master Data module.'
    Add-TextParagraph 'An Item belongs to a Company and may be used as either a BOM parent or component. A BOM header identifies the manufactured parent Item and revision, while BOM lines define the component Items and their required quantities.'
    Add-TextParagraph 'A Routing belongs to an Item and contains ordered Routing operations. Operations may reference Work Centers, while Work Centers may use work calendars composed of weekly shifts and date-specific exceptions. These relationships provide the production structure later snapshotted into Work Orders.'

    Add-TextParagraph 'Sales and Planning ERD' 'Heading 3'
    Add-TextParagraph 'The Sales and Planning module connects customer demand with material requirements. It includes Sales Orders, Sales Order lines, planning demands, MRP runs, run-demand snapshots, requirement lines, and supply suggestions.'
    Add-Placeholder 'SALES AND PLANNING ERD'
    Add-SequenceCaption 'Figure' 'ERD of the Sales and Planning module.'
    Add-TextParagraph 'A Sales Order contains one or more Sales Order lines. Confirmed lines generate planning demand, while an MRP run captures an immutable demand snapshot through mrp_run_demands.'
    Add-TextParagraph 'Requirement lines may form a hierarchy through parent_requirement_line_id, representing multi-level BOM explosion. A shortage requirement produces a supply suggestion that may recommend either purchasing or internal manufacturing.'

    Add-TextParagraph 'Purchasing ERD' 'Heading 3'
    Add-TextParagraph 'The Purchasing module represents external material supply. It includes suppliers, supplier-specific Item data, Purchase Requisitions, Purchase Orders, Goods Receipts, and their corresponding lines.'
    Add-Placeholder 'PURCHASING ERD'
    Add-SequenceCaption 'Figure' 'ERD of the Purchasing module.'
    Add-TextParagraph 'item_suppliers establishes the sourcing relationship between an Item and Supplier, including lead time, minimum order quantity, price, currency, and preferred-supplier status.'
    Add-TextParagraph 'A Purchase Requisition contains requested Item lines and may be converted into a Purchase Order. Purchase Order lines retain their source requisition line where applicable. Goods Receipt lines reference Purchase Order lines and the inventory movement created by posting the receipt.'

    Add-TextParagraph 'Inventory and Warehouse ERD' 'Heading 3'
    Add-TextParagraph 'This module manages item tracking policies, planning settings, inventory balances, lots, serial numbers, and append-only stock movements.'
    Add-Placeholder 'INVENTORY AND WAREHOUSE ERD'
    Add-SequenceCaption 'Figure' 'ERD of the Inventory and Warehouse module.'
    Add-TextParagraph 'Inventory is identified by Item, Warehouse, and optional lot or serial information. stock_balances provides query-optimized on-hand, reserved, and quality-hold quantities. stock_movements preserves the immutable history of receive, issue, adjustment, reversal, and lot-status transactions.'
    Add-TextParagraph 'Item-Warehouse settings store safety stock, reorder point, and lead-time configuration used by planning and operational alerts. Lot and serial statuses determine whether a quantity is eligible for reservation, issue, MRP, or fulfillment.'

    Add-TextParagraph 'Manufacturing Execution and Quality ERD' 'Heading 3'
    Add-TextParagraph 'This module records the internal production lifecycle from Work Order creation through material consumption, WIP reporting, finished-goods receipt, and quality disposition.'
    Add-Placeholder 'MANUFACTURING EXECUTION AND QUALITY ERD'
    Add-SequenceCaption 'Figure' 'ERD of the Manufacturing Execution and Quality module.'
    Add-TextParagraph 'A Work Order contains immutable component and operation snapshots. Material reservations connect component requirements with eligible Warehouse and lot stock. Material Issues consume those reservations and connect each issue line to its inventory movement.'
    Add-TextParagraph 'WIP transactions and Production Executions preserve actual manufacturing evidence. Production Receipt lines connect output Items, Warehouses, lots or serials, and stock movements. Quality dispositions are stored separately so receipt approval and final quality acceptance remain distinct decisions.'

    Add-TextParagraph 'Cross-Module Data Relationships' 'Heading 3'
    Add-TextParagraph 'The six modules form one connected production model rather than six isolated databases. The principal cross-module relationships are summarized below.'
    Add-SequenceCaption 'Table' 'Cross-module data relationships.'
    Add-ReportTable @(
        @('Relationship chain', 'Purpose'),
        @('Company -> Plant -> Warehouse', 'Establishes organizational ownership and authorization scope.'),
        @('Item -> BOM / Routing / Inventory / Purchasing', 'Connects product definition with planning, sourcing, and stock.'),
        @('Sales Order Line -> Planning Demand -> MRP Run', 'Preserves the origin of demand entering material planning.'),
        @('MRP Requirement -> Supply Suggestion', 'Records shortage analysis and the recommended BUY or MAKE response.'),
        @('Purchase Order -> Goods Receipt -> Stock Movement', 'Connects supplier delivery with the inventory ledger.'),
        @('Work Order -> Material Issue -> Stock Movement', 'Connects production consumption with inventory.'),
        @('Work Order -> Production Receipt -> Quality Disposition', 'Connects manufactured output with approval and QC status.'),
        @('Work Order Allocation -> Sales Order Line', 'Connects quality-approved output back to customer demand.')
    )

    # Business rules
    Add-TextParagraph 'Business Rules and State Controls' 'Heading 2'
    Add-TextParagraph 'OmniPlant applies validation at both the document and inventory levels. The principal rules are summarized in the following table.'
    Add-SequenceCaption 'Table' 'Core business validation rules.'
    Add-ReportTable @(
        @('Rule', 'Enforced contract'),
        @('Quantity', 'Transactional, Sales Order, and production quantities must be greater than zero.'),
        @('Lot eligibility', 'HOLD, REJECTED, and expired lots are excluded from availability, reservation, MRP, and fulfillment.'),
        @('Demand', 'Only confirmed and open demand participates in planning and allocation.'),
        @('Snapshot', 'Work Orders retain immutable BOM and Routing evidence.'),
        @('Release', 'All component requirements must be fully reserved before Work Order release.'),
        @('Issue', 'Only a posted Material Issue decreases inventory; over-BOM issue requires reason and permission.'),
        @('Receipt', 'Submission has no inventory effect; only approval creates finished-goods inventory.'),
        @('Quality', 'Approved output remains on HOLD until disposition; only AVAILABLE output satisfies demand.'),
        @('Mutation safety', 'Integrity-sensitive commands require confirmation and stable idempotency-key reuse on retry.')
    )

    # API/security
    Add-TextParagraph 'API, Security, and Transaction Resilience' 'Heading 2'
    Add-TextParagraph 'Every backend response uses a standard envelope containing a response code, message, and optional result. Paginated endpoints additionally return page, size, total element, and total-page information.'
    Add-TextParagraph 'Authentication uses JWT access tokens together with rotating refresh tokens. After authentication, the client loads the authoritative user profile, permissions, role assignments, and access scopes. The frontend uses this information to control navigation and action visibility, while the backend performs the final authorization check for every protected operation.'
    Add-TextParagraph 'Authorization combines permissions with organizational scope. A user may have permission to manage Work Orders but remain limited to a specific Plant. Requests outside the assigned scope are rejected even when the general permission is present.'
    Add-TextParagraph 'State-changing requests use stable idempotency keys. Retrying the same logical action reuses its original key, preventing duplicate inventory movements, MRP runs, Material Issues, Goods Receipts, or Production Receipts during network failures.'

    # UI design
    Add-TextParagraph 'Frontend Interaction Design' 'Heading 2'
    Add-TextParagraph 'The frontend is designed primarily for desktop and tablet operation. Dense lists, filters, status indicators, and forms follow a consistent ERP interaction model.'
    Add-TextParagraph 'Loading states use Skeleton placeholders. Empty states explain why no records are available and display creation actions only when the user has permission. Validation and API errors preserve entered values so the user can correct or retry a request without re-entering the entire form.'
    Add-TextParagraph 'Critical state changes require explicit confirmation. These actions include Sales Order confirmation, BOM and Routing activation, Work Order release, Material Issue posting, receipt approval or rejection, and quality disposition.'
    Add-TextParagraph 'All user-visible content is maintained in English and Vietnamese translation namespaces. Status labels and validation messages use consistent terminology across list, detail, and transaction screens.'

    # Deployment
    Add-TextParagraph 'System Operation and Deployment' 'Heading 2'
    Add-TextParagraph 'A standard local environment consists of PostgreSQL, Redis, the Spring Boot backend, and the Next.js frontend. PostgreSQL migrations are managed through Flyway. The backend normally runs on port 8080, while the frontend runs on port 3000.'
    Add-TextParagraph 'The infrastructure must be healthy before operational testing begins. After startup, the tester verifies authentication, role and scope loading, Plant selection, API health, and migration status. End-to-end verification then proceeds through demand, planning, supply conversion, production or purchasing execution, inventory posting, quality disposition, and audit review.'
    Add-SequenceCaption 'Table' 'Operational startup and verification checklist.'
    Add-ReportTable @(
        @('Operational step', 'Expected check'),
        @('1. Start infrastructure', 'PostgreSQL and Redis are healthy and Flyway migrations are complete.'),
        @('2. Start API', 'Actuator health reports UP and OpenAPI is reachable.'),
        @('3. Start frontend', 'The application loads without console errors and unauthenticated users are redirected to Login.'),
        @('4. Authenticate', 'Profile, permissions, scopes, and default Plant are loaded.'),
        @('5. Select Plant', 'Queries refetch without displaying stale data from another Plant.'),
        @('6. Execute workflow', 'Confirmation and idempotent commands are used and document status is verified after each transition.'),
        @('7. Verify results', 'Movements, lot status, Dashboard, Sales Order fulfillment, and audit evidence are checked.')
    )

    Add-TextParagraph 'Chapter Summary' 'Heading 2'
    Add-TextParagraph 'This chapter presented the architectural, functional, process, and data design of OmniPlant. The use case diagram defined the responsibilities of Administrator, Manager, and Operator, while the swimlane activity diagram described the execution sequence and automated System behavior.'
    Add-TextParagraph 'The six ERDs demonstrate how administration, master data, sales and planning, purchasing, inventory, and manufacturing execution are separated into readable modules while remaining connected through explicit relationships. Together, these designs establish traceability from organizational access and customer demand to material planning, purchasing or production, inventory posting, and quality-controlled output.'

    # Refresh numbering, the table of contents, and lists of figures/tables.
    foreach ($story in $doc.StoryRanges) {
        $current = $story
        while ($null -ne $current) {
            $current.Fields.Update() | Out-Null
            $current = $current.NextStoryRange
        }
    }
    foreach ($toc in $doc.TablesOfContents) { $toc.Update() }
    foreach ($tof in $doc.TablesOfFigures) { $tof.Update() }

    $doc.Save()
    $doc.Close()
    $doc = $null
    $word.Quit()
    $word = $null

    [System.IO.File]::Copy($workingPath, $resolvedPath, $true)

    Write-Output "DOCUMENT=$resolvedPath"
    Write-Output "BACKUP=$backupPath"
}
finally {
    if ($null -ne $doc) {
        try { $doc.Close($false) } catch {}
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($doc) | Out-Null
    }
    if ($null -ne $word) {
        try { $word.Quit() } catch {}
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
