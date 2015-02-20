//--------------------------------------------------------------------------------------
//
//   AuditTrail - Parse Salesforce Audit trail csv file
//
//   Date         Who      Description
//
// 02/17/15       mjm      Added section debug hook; added ignore list to command
//                         line args; started working on AuditEntry class
// 02/18/15       mjm      - Change to add AuditEntry data for each entry
//                         - Added filter by user
//                         - Added user and date to report output
// 02/20/15       mjm      - More report fixups; added filter by user
//
//--------------------------------------------------------------------------------------
@Grab( 'com.xlson.groovycsv:groovycsv:1.0' )
import com.xlson.groovycsv.CsvParser
import groovy.transform.Field
import groovy.transform.ToString

def version = "01.00.02"

// Declare maps

def profiles = [:]
def classes = [:]
def triggers = [:]
def objects = [:]
def pages = [:]
def workflows = [:]
def layouts = [:]
def fields = [:]
def tabs = [:]
def components = [:]
def validations = [:]
def approvals = [:]

def debug = false
def verbose = false
def listSkippedSections = true
def listIgnored = false
def stopOnError = false
def apiVersion = '32.0'

def linesProcessed = 0
def numIgnoreSkipped = 0
def numOldSkipped = 0
def numUsersSkipped = 0
def numMalformed = 0
def totalLines = 0
def numSectionSkipped = 0
def numManagedPackagesSkipped = 0
def numPackageEntries = 0
def excludeUser = null
def includeUser = null

@ToString(includeNames=true, includeFields=true)
class AuditEntry {

  private String section
  private String action
  private String object
  private String entity
  private String user
  private String dateChanged

  AuditEntry(){
	this.section = null
	this.object = null
	this.user = null
	this.dateChanged = null
	this.entity = null
	this.action = null
  }
  
  AuditEntry(String sec, String action, String obj, String name, String usr, String dt) {
	this.section = sec
	this.object = obj
	this.user = usr
	this.dateChanged = dt
	this.entity = name
	this.action = action
  }
  

}  // end class AuditEntry

// NOTE: I had to define these as seen below, because if the methods are defined
// with a value, they are not included in the scope.. this is a Groovy thing.

//--------------------------------------------------------------------------------------
//
//   handlePlaceHolder
//
//--------------------------------------------------------------------------------------
def handlePlaceHolder
handlePlaceHolder = { auditEntry ->
  println "(handlePlaceHolder) enter, section: " + auditEntry.section
  if (stopOnError) {
	println "ERROR: stopping due to unhandled section: " + auditEntry.section
	System.exit(1)
  }
}

//--------------------------------------------------------------------------------------
//
//   handleIgnoreSection
//
//--------------------------------------------------------------------------------------
def handleIgnoreSection
handleIgnoreSection = { auditEntry ->
  if (debug) {
	println "(handleIgnoreSection) enter, ignoring section: " + auditEntry.section
  }
}

//--------------------------------------------------------------------------------------
//
//   handleApexClass
//
//--------------------------------------------------------------------------------------

def handleApexClass
handleApexClass = { auditEntry ->
  className = auditEntry.action.split(' ')[1]
  
  if (!classes.containsKey(className)) {
	classes[className] = auditEntry
  }
}

//--------------------------------------------------------------------------------------
//
//   handleCustomize
//
//--------------------------------------------------------------------------------------

def handleCustomize
handleCustomize = { auditEntry ->
  matcher = (auditEntry.action =~ "(Created|Changed) (.*) page layout (.*)")

  if (matcher.matches()) {
	obj = matcher[0][1]
	ent = matcher[0][2]
  
	if (objects.containsKey(ent)) {
	  auditEntry.object = obj
	  auditEntry.entity = ent
	  
	  objects[ent] = auditEntry
	}
  }
}

//--------------------------------------------------------------------------------------
//
//   handleCustomizeOpportunities
//
//--------------------------------------------------------------------------------------

def handleCustomizeOpportunities
handleCustomizeOpportunities = { auditEntry ->
  what = auditEntry.action.split(' ')[1]

  if (!objects.containsKey(what)) {
	objects[what] = auditEntry
  } 
}

//--------------------------------------------------------------------------------------
//
//   handleValidationRule
//
//--------------------------------------------------------------------------------------

def handleValidationRule
handleValidationRule = { auditEntry ->
  //http://stackoverflow.com/questions/1363643/regex-over-multiple-lines-in-groovy
  
  newMatcher = (auditEntry.action =~ "New (.*) validation rule (.*)")
  
  if (newMatcher.matches()) {
	if (debug) println "($totalLines) new matcher0 " + newMatcher[0][0]
	if (debug) println "($totalLines) new matcher1 " + newMatcher[0][1]
	if (debug) println "($totalLines) new matcher2 " + newMatcher[0][2]
	
	def obj = newMatcher[0][1].replace('"','')
	def rule = newMatcher[0][2].replace('"','')

	auditEntry.object = obj
	auditEntry.entity = rule
	
	//printf(">>>> new match for obj (key): %s, rule (value): %s\n", obj, rule)
	
	if (!validations.containsKey(rule)) {
	  validations[rule] = auditEntry
	}
  }
  
  // Changed to match embedded newlines
  //changeMatcher = (auditEntry.action =~ "Changed (.*) for (.*) validation (.*) from (.*)/")
  changeMatcher = (auditEntry.action =~ /Changed (.*) for (.*) validation (.*) from (?ms)(.*)/)
  
  if (debug) println ">>>> changeMatcher: $changeMatcher"
  
  if (changeMatcher.matches()) {
	if (debug) println "($totalLines) change matcher0 " + changeMatcher[0][0]
	if (debug) println "($totalLines) change matcher1 " + changeMatcher[0][1]
	if (debug) println "($totalLines) change matcher2 " + changeMatcher[0][2]
	if (debug) println "($totalLines) change matcher3 " + changeMatcher[0][3]
	
	def obj = changeMatcher[0][2].replace('"','')
	def rule = changeMatcher[0][3].replace('"','')

	auditEntry.entity = rule
	auditEntry.object = obj
	
	if (debug) printf(">>>> change match for obj (key): %s, rule (value): %s\n",  obj, rule)
	
	if (!validations.containsKey(rule)) {
	  validations[rule] = auditEntry
	}
  }
}

//--------------------------------------------------------------------------------------
//
//   handleWorkflow
//
//--------------------------------------------------------------------------------------

def handleWorkflow
handleWorkflow = { auditEntry ->
  if (debug) println "($totalLines) Action: " + auditEntry.action

  def obj
  def rule
  
  matcher = (auditEntry.action.value =~ "(.*) workflow rule (.*) for Object: (.*)")
  
  if (matcher.matches()) {
	what = matcher[0][1]
	obj = matcher[0][3]
	rule = matcher[0][2]
	
	if (debug) println "($totalLines) matcher: obj: " + obj + ", rule: " + rule
  }
  
  matcher2 = (auditEntry.action.value =~ "workflow rule (.*) for Object: (.*)")
  
  if (matcher2.matches()) {
	obj = matcher2[0][2]
	rule = matcher2[0][1]
	
	if (debug) println "($totalLines) matcher2: obj: " + obj + ", rule: " + rule
  }
  
  matcher3 = (auditEntry.action.value =~ "(.*) Field Update (.*) for Object: (.*)")
  
  if (matcher3.matches()) {
	what = matcher3[0][1]
	obj = matcher3[0][3]
	rule = matcher3[0][2]
	if (debug) println "($totalLines) matcher3: what: " + what + ", obj: " + obj + ", rule: " + rule
  }

  auditEntry.object = obj
  auditEntry.entity = rule
  
  if (rule != null) {
	if (!workflows.containsKey(rule)) {
	  workflows[rule] = auditEntry
	} 
  }
}

//--------------------------------------------------------------------------------------
//
//   handleComponent
//
//--------------------------------------------------------------------------------------

def handleComponent
handleComponent = { auditEntry ->
  matcher = (auditEntry.action =~ "(Created|Changed) Component (.*)")

  comp = matcher[0][1]
  
  if (matcher.matches()) {
	if (components.containsKey(comp)) {
	  //printf("Component found: %s\n", matcher[0][1])
	  components[comp] = auditEntry
	}
  }
}

//--------------------------------------------------------------------------------------
//
//   handleManageUsers
//
//--------------------------------------------------------------------------------------

def handleManageUsers
handleManageUsers = { auditEntry ->
  matcher = (auditEntry.action =~ "Changed profile (.*): (.*)")

  if (matcher.matches()) {
	//printf("Custom Field %s, Object: %s\n", matcher[0][1], matcher[0][2])
	profile = matcher[0][1]
	profiles[profile] = auditEntry
  }
}

//--------------------------------------------------------------------------------------
//
//   handleApprovalProcess
//
//--------------------------------------------------------------------------------------

def handleApprovalProcess
handleApprovalProcess = { auditEntry ->
  if (debug) println "($totalLines) \n>>>> Page: what: $what, action: $auditEntry.action\n"
  
  def obj
  def name
  
  matcher = (auditEntry.action =~ "Changed Process Step: (.*) for Approval Process: (.*) for Object: (.*)")
  
  if (matcher.matches()) {
	obj = matcher[0][3]
	name = matcher[0][2]

	auditEntry.entity = name
	auditEntry.object = obj
	
	//printf(">>>> App. Process, obj: " + obj + ", what: " + name)
	
	if (!approvals.containsKey(name)) {
	  approvals[name] = auditEntry
	}
  }
}

//--------------------------------------------------------------------------------------
//
//   handlePage
//
//--------------------------------------------------------------------------------------

def handlePage
handlePage = { auditEntry ->
  def obj
  def procName
  
  pageMatcher = (auditEntry.action =~ "(.*) Page (.*)")
  
  if (pageMatcher.matches()) {
	if (pageMatcher[0][1].equals('Created') ||
		pageMatcher[0][1].equals('Changed')) {
	  
	  procName = pageMatcher[0][1]
	  obj = pageMatcher[0][2]
	  
	  //println "($totalLines) >>>> Page obj: " + obj + ", name: " + procName
	  //println "($totalLines) >>>> action: $auditEntry.action"

	  auditEntry.entity = procName
	  
	  if (!pages.containsKey(obj)) {
		pages[obj] = auditEntry
	  }
	}
  }
}

//--------------------------------------------------------------------------------------
//
//   handleCustomObjects
//
//--------------------------------------------------------------------------------------

def handleCustomObjects
handleCustomObjects = { auditEntry ->
  if (debug) println "($totalLines) >>>> action: $auditEntry.action"
  
  fieldMatcher = (auditEntry.action =~ "Created custom field (.*) \\((.*)\\) on (.*)")
  
  if (fieldMatcher.matches()) {
	//printf("Custom Field: %s, Object: %s, Type: %s\n",
	//	   fieldMatcher[0][1], fieldMatcher[0][3], fieldMatcher[0][2])

	entity = fieldMatcher[0][1]
	object= fieldMatcher[0][3]

	auditEntry.entity = entity
	auditEntry.object= object
	
	if (!fields.containsKey(object)) {
	  fields[object] = auditEntry
	}
  }
  
  objectMatcher = (auditEntry.action =~ "Created custom object: (.*)")
  
  if (objectMatcher.matches()) {
	//printf("Custom Field %s, Object: %s\n", objectMatcher[0][1], objectMatcher[0][2])
	assert(!objects.containsKey(objectMatcher[0][1]))

	auditEntry.entity = objectMatcher[0][1]
	objects[objectMatcher[0][1]] = auditEntry
  }

  layoutMatcher = (auditEntry.action =~ "Changed (.*) page layout (.*)")
  
  if (layoutMatcher.matches()) {
	
	if (!layouts.containsKey(layoutMatcher[0][2])) {
	  obj = layoutMatcher[0][2]
	  entity = layoutMatcher[0][1]
	  auditEntry.entity = entity
	  auditEntry.object = obj
	  
	  layouts[entity] = auditEntry
	}
  }
}

//--------------------------------------------------------------------------------------
//
//   handleCustomTabs
//
//--------------------------------------------------------------------------------------

def handleCustomTabs
handleCustomTabs = { auditEntry ->
  if (debug) println "($totalLines) >>>> action: $auditEntry.action"
  
  //fieldMatcher = (auditEntry.action =~ "(Created|Changed) custom Visualforce Page tab: (.*)")
  fieldMatcher = (auditEntry.action =~ "(Created|Changed) custom (.*) tab: (.*)")
  
  if (fieldMatcher.matches()) {
	entity = fieldMatcher[0][3]
	what = fieldMatcher[0][2]
	cmd = fieldMatcher[0][1]

	//println "(handleCustomTabs) cmd: $cmd, what: $what, entity: $entity"
	
	auditEntry.entity = entity
	auditEntry.object = what

	if (!tabs.containsKey(entity)) {
	  tabs[entity] = auditEntry
	}
  }
}

//--------------------------------------------------------------------------------------
//
//   handleApexTrigger
//
//--------------------------------------------------------------------------------------

def handleApexTrigger
handleApexTrigger = { auditEntry ->

  triggerName = auditEntry.action.split(' ')[1]
  triggers[triggerName] = auditEntry
}


// This works like a global variable so this can be seen in the 'doDebug' method below
@Field def sectionDebug = null
def doDebug(section, flag) {
  //println "(doDebug) enter, section $section, flag $flag"
  
  if ((sectionDebug != null) && (sectionDebug.contains(section) && flag)) {
	//println "(doDebug) returning true"
	return true
  } else {
	return false
  }
}

def pluralsMap = [
                  'Opportunities': 'Opportunity',
				  'Surveys': 'Survey'
                 ]
  
// This list lets us ignore user stuff, such as Activate and Deactivate, log-ins, password
// changes, etc.

def ignoreList = ['Activated',
				  'Deactivated',
				  'Logged',
				  'Password',
				  'Requested',
				  'Granted',
				  'For',
				  'Email',
				  'Feed',
				  'Organization']

// This maps all of our expected section names to a handler function
				  
def sectionMap = [
  'Apex Class' : handleApexClass,
  'Apex Trigger' : handleApexTrigger,
  'Approval Process' : handleApprovalProcess,
  'Chatter Settings' : handlePlaceHolder,
  'Company Information' : handleIgnoreSection,
  'Component' : handleComponent,
  'Connected Apps' : handlePlaceHolder,
  'Critical Updates' : handlePlaceHolder,
  'Custom App Licenses' : handlePlaceHolder,
  'Custom Apps' : handlePlaceHolder,
  'Custom Objects' : handleCustomObjects,
  'Custom Tabs' : handleCustomTabs,
  'Customize Accounts' : handleCustomize,
  'Customize Contacts' : handleCustomize,
  'Customize Home' : handleCustomize,
  'Customize Leads' : handleCustomize,
  'Customize Opportunities' : handleCustomize,
  'Customize Opportunity Products' : handleCustomize,
  'Customize Price Books' : handleCustomize,
  'Customize Products' : handleCustomize,
  'Customize Quote Lines' : handleCustomize,
  'Customize Quotes' : handleCustomize,
  'Customize Users' : handleCustomize,
  'Data Export' : handlePlaceHolder,
  'Data Management' : handlePlaceHolder,
  'Deployment Connections' : handleIgnoreSection,
  'Feed Tracking' : handlePlaceHolder,
  'Groups' : handlePlaceHolder,
  'Inbound Change Sets' : handleIgnoreSection,
  'Manage Users' : handleIgnoreSection,
  'Page' : handlePage,
  'Partner Relationship Management' : handlePlaceHolder,
  'Security Controls' : handleIgnoreSection,
  'Sharing Rules' : handlePlaceHolder,
  'Static Resource' : handleIgnoreSection,
  'Track Field History' : handleIgnoreSection,
  'Validation Rules' : handleValidationRule,
  'Workflow Rule' : handleWorkflow
]

def cli = new CliBuilder(usage: 'AuditTrail.groovy -[hfpDsdxuveai]')

cli.f(args:1, argName:'inputFile', 'Salesforce audit trail csv file')
cli.s(args:1, argName:'sectionDebug', 'Section to debug')
cli.x(args:1, argName:'excludeUser', 'Exclude changes by user portion of email address')
cli.u(args:1, argName:'includeUser', 'Only show changes by user portion of email address')
cli.i(args:1, argName:'ignoreList', 'List of csv keywords to ignore')
cli.p(args:1, argName:'packagefile', 'Generate package.xml file')
cli.D(args:1, argName:'startDate', 'oldest date for changes, format: mm/dd/yyyy')
cli.a(args:1, argName:'apiVersion', 'Salesforce API version (only with -p option)')
cli.h(longOpt:'help','Show this help text')
cli.v(longOpt:'verbose','Verbose mode')
cli.d(longOpt:'debug','Debug on')
cli.e('Stop on Error')

def options = cli.parse(args)

if (!options) {
    return
}

// Show usage text when -h or --help option is used.
if (options.h) {
    cli.usage()
    return
}

if (options.d) {
	println "Set debug from command line"
	debug = true
}

if (options.s) {
  sectionDebug = options.s
  println "Got sectionDebug from command line: $sectionDebug"
}

if (options.x) {
  excludeUser = options.u
  println "Got exclude user from command line: $excludeUser"
}

if (options.u) {
  includeUser = options.u
  println "Got include user from command line: $includeUser"
}

if (options.e) {
	println "Set stopOnError from command line"
	stopOnError = true
}


if (options.v) {
	println "Set verbose from command line"
	verbose = true
}

def managedPackagesList = ['BMXP','dsfs','QConfig','TMS','DocuSign','spotlightfs']

if (options.i) {
  tmp = options.i
  ignoreList = tmp.split(',')
  println "Got ignoreList from command line: $ignoreList"
}

def whatIgnoreList = ['email','sandbox','help','new','password']

if (options.a) {
  apiVersion = options.a
  if (debug) {
	println "Got apiVersion " + apiVersion + " from command line"
  }
}

def startDate

if (options.D) {
	dt = options.D
	println "Got startDate from command line: " + dt
	try {
		startDate = Date.parse('MM/dd/yyyy', dt)
		println "Start Date: " + startDate + " as time: " + startDate.getTime()
	} catch(e) {
		println "ERROR: could not format date: " + dt 
		System.exit(1)
	}
}

def inputFile 
def packageFile

if (options.f) {
	inputFile = options.f
} else {
	println "ERROR: must provide csv file"
	System.exit(1)
}

if (options.p) {
	packageFile = options.p
} 

if (debug) {
	println "packageFile: " + packageFile
}

println "AuditTrail.groovy version $version starting up"

def f = new File(inputFile).withReader {
  	def csvc = CsvParser.parseCsv( it )

	//f.splitEachLine(',') {
	csvc.each {

		totalLines += 1

		// Skip first line with headers

		if (totalLines == 1) {
			return
		}

		def dt = it['Date'].split(' ')[0]
		def action = it['Action']
		def section = it['Section']
		def user = it['User'].split('@')[0]

		def ae = new AuditEntry()
		ae.section = section
		ae.dateChanged = dt
		ae.user = user
		ae.action = action
		
		keyword = null
		what = null
		keyword = action.split(' ')[0]
		what = action.split(' ')[1]
		remainder = action.substring(action.indexOf(what),)

		// Try and filter out managed package stuff

		def skipThis = false
		
		managedPackagesList.each {
		  if (action.contains(it)) {
			if (debug) println "\n($totalLines) **** action: $action"
			if (debug) println "($totalLines) **** it: $it"
			if (debug) println "($totalLines) **** Skipping managed package entry for $it\n"
			numManagedPackagesSkipped++
			skipThis = true
			return
		  }
		}

		if (skipThis) {
		  return
		}

		// Filter out by user if set

		if ((excludeUser != null) && (user.equals(excludeUser))) {
		  println "($totalLines) ++++++++++ Ignoring user: " + user
		  numUsersSkipped++
		  return
		}
		
		// Include only user if set

		if ((includeUser != null) && (!user.equals(includeUser))) {
		  println "($totalLines) ++++++++++ Ignoring user: " + user
		  numUsersSkipped++
		  return
		}
		
		// If the keyword is not one we care about, just continue

		if ((keyword != null) && ignoreList.contains(keyword)) {
		  if (listIgnored) {
			println "($totalLines) ++++++++++ Ignoring keyword: " + keyword + ", section: $section"
		  }
		  numIgnoreSkipped++
		  return
		}

		// We don't care about reset passwords

		if (whatIgnoreList.contains(what)) {
		  if (listIgnored) {
			println "($totalLines) ++++++++++ Ignoring what: " + what
		  }
		  numIgnoreSkipped++
		  return
		}

		// Next, check for the date if the user entered one.. ignore things older than that date

		Date changeDate = null

		try {
		  changeDate = Date.parse('MM/dd/yyyy', dt)
		} catch(e) {
		  println "($totalLines) ERROR: caught Date parse exception, skipping item: " + it[0]

		  if (stopOnError) {
			System.exit(1)
		  }

		  numMalformed += 1
		  return
		}

		//Date changeDate = Date.parse(it[0].replace('"','').split(' ')[0])

		if (debug) {
			println "($totalLines) Date: " + changeDate + ", as time: " + changeDate.getTime()
		}

		if (changeDate.getTime() < startDate.getTime()) {
			if (debug) {
				println "($totalLines) Skipping date '$changeDate' entry older than start date '$startDate'"
			}

			numOldSkipped += 1
			return
		}
		
		if (!sectionMap.keySet().contains(section)) {
		  if (listSkippedSections) {
			println "WARNING: Skipping section: " + section
		  }
		  numSectionSkipped++
		  return
		} else {
		  // Call the handler method
		  //println "($totalLines) calling sectionMap handler........ for section: $section"
		  //println "($totalLines) calling sectionMap ae: " + ae
		  //println "($totalLines) calling sectionMap dt: " + dt
		  sectionMap[section](ae)
		}
		
		if (verbose) {
			println "\n($totalLines) Section \"$section\""
			println "($totalLines) \tProcessing keyword \"$keyword\", what: \"$what\", date: $dt"
			println "($totalLines) \t.... remainder: $remainder"
		}

	    linesProcessed++
		  
	}  // end each closure
	
}  // end new File

println "\nProcessing Summary for changes since $startDate"
println "Found $totalLines lines in file (minus 1 for header)"
println "Number managed package entries skipped: $numManagedPackagesSkipped"
println "Number sections skipped: $numSectionSkipped"
println "Number users skipped: $numUsersSkipped"
println "Number in ignoreList skipped: $numIgnoreSkipped"
println "Processed $linesProcessed Lines"
println "Skipped $numOldSkipped old entries"
println "Found $numMalformed mal-formed entries"

numPackageEntries += profiles.size()
numPackageEntries += classes.size()
numPackageEntries += triggers.size()
numPackageEntries += objects.size()
numPackageEntries += fields.size()
numPackageEntries += tabs.size()
numPackageEntries += validations.size()
numPackageEntries += pages.size()
numPackageEntries += layouts.size()
numPackageEntries += components.size()
numPackageEntries += workflows.size()
numPackageEntries += approvals.size()

if (options.p) {
  println "Number of package entries: $numPackageEntries"
}

// If the user did not ask for a package file, print the summary to the screen

if (options.p == false) {

  println "\n\nSalesforce Audit Trail report for file: $inputFile\n\n"
  
  if (includeUser != null) {
	println "Only entries by user: $includeUser shown as requested"
  }

  if (excludeUser != null) {
	println "Only showing entries not by user: $excludeUser shown as requested"
  }
  
  if (profiles.size() > 0) {
	println "\nProfiles ---------------------------------------------------------------\n"
	printf("  %-25s %-25s %-15s\n\n", "Object", "Rule Name", "Last ChangedBy", "Last Date Changed")
	
	profiles.each{
	  printf("  %-40s %-25s %-15s\n", it.key, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + profiles.size() + "\n"
  }
  
  if (classes.size() > 0) {
	println "\nApex Classes ---------------------------------------------------------------\n"
	printf("  %-25s %-25s %-15s\n\n", "Class", "Last ChangedBy", "Last Date Changed")
	classes.each{
	  //println 'Key: ' + it.key + ', Value: ' + it.value 
	  //if (it.key.equals('User')) {
	  //printf("  User: %s\n", it.value)
	  //} else {
	  //printf("  %s\n", it.key)
	  //}
	  printf("  %-25s %-25s %-15s\n", it.key, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + classes.size() + "\n"
  }
  
  if (triggers.size() > 0) {
	println "\nTriggers ---------------------------------------------------------------\n"
	printf("  %-25s %-25s %-15s\n\n", "Trigger", "Last ChangedBy", "Last Date Changed")
	
	triggers.each{
	  printf("  %-25s %-25s %-15s\n", it.key, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + triggers.size() + "\n"
  }
  
  if (objects.size() > 0) {
	
	println "\nObjects ---------------------------------------------------------------\n"
	printf("  %-25s %-25s %-15s\n\n", "Object", "Last ChangedBy", "Last Date Changed")
	
	objects.each{
	  printf("  %-25s %-25s %-15s\n", it.key, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + objects.size() + "\n"
  }
  
  if (pages.size() > 0) {
	
	println "\nPages ---------------------------------------------------------------\n"
	printf("  %-40s %-25s %-15s\n\n", "Page", "Last ChangedBy", "Last Date Changed")
	
	pages.each{
	  printf("  %-40s %-25s %-15s\n", it.key, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + pages.size() + "\n"
  }
  
  if (workflows.size() > 0) {
	println "\nWorkflow Rules ---------------------------------------------------------------\n"
	printf("  %-25s %-55s %-25s %-15s\n\n", "Object", "Rule Name", "Last ChangedBy", "Last Date Changed")

	workflows.each{
	  if (it.value.entity.length() > 55) {
		ruleName = it.value.entity.substring(0,50) + "..."
	  } else {
		ruleName = it.value.entity
	  }
	
	  printf("  %-25s %-55s %-25s %-15s\n", it.value.object, ruleName, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + workflows.size() + "\n"
  }
  
  if (validations.size() > 0) {
	
	println "\nValidation Rules ---------------------------------------------------------------\n"
	printf("  %-25s %-40s %-25s %-15s\n\n", "Object", "Rule Name", "Last ChangedBy", "Last Date Changed")
	
	validations.each{
	  printf("  %-25s %-40s %-25s %-15s\n", it.value.object, it.value.entity, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + validations.size() + "\n"
  }
  
  if (layouts.size() > 0) {
	println "\nLayouts ---------------------------------------------------------------\n"
	printf("  %-25s %-40s %-25s %-15s\n\n", "Object", "Layout", "Last ChangedBy", "Last Date Changed")
	
	layouts.each{
	  printf("  %-25s %-40s %-25s %-15s\n", it.value.entity, it.value.object, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + layouts.size() + "\n"
  }
  
  if (fields.size() > 0) {
	println "\nFields ---------------------------------------------------------------\n"
	printf("  %-25s %-40s %-25s %-15s\n\n", "Object", "Field", "Last ChangedBy", "Last Date Changed")
	
	fields.each{
	  printf("  %-25s %-40s %-25s %-15s\n", it.key, it.value.entity, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + fields.size() + "\n"
  }
  
  if (tabs.size() > 0) {
	println "\nTabs ---------------------------------------------------------------\n"
	printf("  %-25s %-40s %-25s %-15s\n\n", "Tab", "Type", "Last ChangedBy", "Last Date Changed")
	
	tabs.each{
	  printf("  %-25s %-40s %-25s %-15s\n", it.value.entity,it.value.object, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + tabs.size() + "\n"
  }
  
  if (components.size() > 0) {
	println "\nComponents ---------------------------------------------------------------\n"
	printf("  %-25s %-40s %-25s %-15s\n\n", "Component", "", "Last ChangedBy", "Last Date Changed")
	
	components.each{
	  printf("  %-25s %-40s %-25s %-15s\n", it.key, it.value.entity, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + components.size() + "\n"
  }
  
  if (approvals.size() > 0) {
	println "\nApproval Processes ---------------------------------------------------------------\n"
	printf("  %-25s %-40s %-25s %-15s\n\n", "Object", "Rule Name", "Last ChangedBy", "Last Date Changed")
	
	approvals.each{
	  printf("  %-25s %-40s %-25s %-15s\n", it.value.object, it.value.entity, it.value.user, it.value.dateChanged)
	}
	println "\nTotal: " + approvals.size() + "\n"
  }

  if (!options.p) {
	println "\nNumber of entries: $numPackageEntries"
  }
}

// Lastly, if package file was requested, generate one

if (options.p) {

  genDate = new Date()
  
  new File("$packageFile").withWriter { out ->
	out.println '<?xml version="1.0" encoding="UTF-8"?>'
	out.println '<Package xmlns="http://soap.sforce.com/2006/04/metadata">'

	out.println '<!-- Generated by AuditTrail version ' + version + ' on ' + genDate + ' -->'
	
	if (classes.size() > 0) {
	  out.println '<types>'
	  
	  classes.each {
		out.println '<members>' + it.key + '</members>'
	  }

	  out.println '<name>ApexClass</name>'
      out.println '</types>'
	}
	
	if (triggers.size() > 0) {
	  out.println '<types>'
	  
	  triggers.each{
		out.println '<members>' + it.key + '</members>'
	  }
	  out.println '<name>ApexTrigger</name>'
      out.println '</types>'
	}

	if (profiles.size() > 0) {
	  out.println '<types>'
	  profiles.each{
		out.println '<members>' + it.key + '</members>'
	  }
	  out.println '<name>Profile</name>'
      out.println '</types>'
	}
	
	if (pages.size() > 0) {
	  out.println '<types>'
	  
	  pages.each{
		out.println '<members>' + it.key + '</members>'
	  }
	  out.println '<name>ApexPage</name>'
	  out.println '</types>'
	}
	
	if (workflows.size() > 0) {
	  out.println '<types>'
	  
	  workflows.each{
		out.println '<members>' + it.key + '</members>'
	  }
	  out.println '<name>Workflow</name>'
	  out.println '</types>'
	}
	
	if (validations.size() > 0) {
	  out.println '<types>'

	  validations.each{
		def obj = it.key
		if (pluralsMap.containsKey(it.key)) {
		  obj = pluralsMap[it.key]
		}
	  
		out.println '<members>' + obj + '.' +it.value + '</members>'
	  }
	  out.println '<name>ValidationRule</name>'
	  out.println '</types>'
	}
	
	if (layouts.size() > 0) {
	  out.println '<types>'
	  
	  layouts.each{
		out.println '<members>' + it.key + '</members>'
	  }
	  out.println '<name>Layout</name>'
	  out.println '</types>'
	}
	
	if (fields.size() > 0) {
	  out.println '<types>'
	  
	  fields.each{
		out.println '<members>' + it.value + '.' + it.key + '</members>'
	  }
	  out.println '<name>CustomField</name>'
	  out.println '</types>'
	}
	
    out.println '<version>' + apiVersion + '</version>'
	out.println '</Package>'

	if (verbose) {
	  println "Created package.xml file: $packageFile"
	}
  }
  
}

