*** Settings ***
Documentation       Browser smoke tests against the React frontend and Spring Boot backend.
Library             Browser    timeout=10s    retry_assertions_for=5s
Suite Setup         Start Test Browser
Suite Teardown      Close Browser    ALL
Test Setup          New Context
Test Teardown       Show Result And Close Context

*** Variables ***
${BASE_URL}              http://localhost:4173
${HEADLESS}              ${TRUE}
${SLOW_MO}               0s
${VIEW_DELAY}            4s
${TIMED}                 css=section[aria-labelledby="tick-title"]
${BROADCAST}             css=section[aria-labelledby="broadcast-title"]
${OPEN}                  [role="status"][data-state="open"]
${CLOSED}                [role="status"][data-state="closed"]
${RECEIVED_AT}           ${TIMED} time.latest-time
${TIMED_MESSAGE}         ${TIMED} .latest-message
${BROADCAST_MESSAGE}     ${BROADCAST} .latest-message
${INTERVAL_INPUT}        ${TIMED} input
${INTERVAL_ERROR}        css=#interval-error
${SEND_FEEDBACK}         css=.broadcast-feedback
${BROADCAST_TOGGLE}      ${BROADCAST} .stream-controls button
${TIMED_TOGGLE}          ${TIMED} .stream-controls button
${INTERVAL_SUBMIT}       ${TIMED} .interval-form button[type="submit"]
${CLIENT_ROWS}          css=.clients tbody tr
${BROADCAST_ROW}        ${CLIENT_ROWS}:has(td:nth-child(2):text-is("Broadcast"))
${TIMED_ROW}            ${CLIENT_ROWS}:has(td:nth-child(2):text-is("ตามเวลา"))

*** Test Cases ***
Receiver Opens Both Streams Automatically
    Open Receiver
    Get Text    ${TIMED_MESSAGE}    starts    I ate
    Wait For Elements State    ${BROADCAST} .latest-empty    visible

Broadcast Reaches Two Receivers In Its Own Stream
    ${first}=    Open Receiver
    ${second}=    Open Receiver
    ${message}=    Send Unique Broadcast
    Get Text    ${SEND_FEEDBACK}    matches    ^ส่งข้อความไปยัง (?:[2-9]|[1-9][0-9]+) ไคลเอนต์แล้ว$

    FOR    ${receiver}    IN    ${first}    ${second}
        Switch Page    ${receiver}
        Get Text    ${BROADCAST_MESSAGE}    ==    ${message}
        Get Text    ${TIMED_MESSAGE}    starts    I ate
    END

Closing Broadcast Leaves Timed Messages Running
    Open Receiver
    Click    ${BROADCAST_TOGGLE}
    Wait For Elements State    ${BROADCAST} ${CLOSED}    visible
    ${previous}=    Get Attribute    ${RECEIVED_AT}    datetime
    Timed Stream Should Receive Another Event    ${previous}
    Wait For Elements State    ${BROADCAST} ${CLOSED}    visible

Invalid Interval Shows Thai Validation And Leaves Timed Stream Running
    Open Receiver
    Fill Text    ${INTERVAL_INPUT}    1e2
    Click    ${INTERVAL_SUBMIT}
    Get Text    ${INTERVAL_ERROR}    ==    กรอกช่วงเวลาเป็นจำนวนเต็มบวกไม่เกิน 2,147,483,647
    Get Attribute    ${INTERVAL_INPUT}    aria-invalid    ==    true
    Get Text    ${TIMED} .connection code    ==    GET /api/events
    ${previous}=    Get Attribute    ${RECEIVED_AT}    datetime
    Timed Stream Should Receive Another Event    ${previous}

Valid Interval Reopens Only The Timed Stream
    Open Receiver
    Evaluate JavaScript    ${BROADCAST} [role="status"]
    ...    (status) => { window.broadcastStates = []; new MutationObserver((changes) => window.broadcastStates.push(...changes.map((change) => change.oldValue))).observe(status, { attributes: true, attributeFilter: ['data-state'], attributeOldValue: true }); }
    Fill Text    ${INTERVAL_INPUT}    2
    ${request}=    Promise To    Wait For Request    ${APP_URL}/api/events?intervalSeconds=2
    Click    ${INTERVAL_SUBMIT}
    Wait For    ${request}
    Get Text    ${TIMED} .connection code    ==    GET /api/events?intervalSeconds=2
    Wait For Elements State    ${TIMED} ${OPEN}    visible
    ${previous}=    Get Attribute    ${RECEIVED_AT}    datetime
    Timed Stream Should Receive Another Event    ${previous}
    Wait For Elements State    ${BROADCAST} ${OPEN}    visible
    ${changes}=    Evaluate JavaScript    ${None}    () => window.broadcastStates
    Should Be Empty    ${changes}

Broadcast Receives Messages After Manual Reconnect
    ${receiver}=    Open Receiver
    Click    ${BROADCAST_TOGGLE}
    Wait For Elements State    ${BROADCAST} ${CLOSED}    visible
    Click    ${BROADCAST_TOGGLE}
    Wait For Elements State    ${BROADCAST} ${OPEN}    visible
    ${message}=    Send Unique Broadcast
    Get Text    ${SEND_FEEDBACK}    matches    ^ส่งข้อความไปยัง [1-9][0-9]* ไคลเอนต์แล้ว$
    Switch Page    ${receiver}
    Get Text    ${BROADCAST_MESSAGE}    ==    ${message}

Client Table Removes The Closed Timed Session
    Open Receiver
    ${unique_interval}=    Evaluate    str(random.randint(100000, 999999))    modules=random
    Fill Text    ${INTERVAL_INPUT}    ${unique_interval}
    Click    ${INTERVAL_SUBMIT}
    ${row}=    Set Variable    ${TIMED_ROW}:has(td:nth-child(3):text-is("${unique_interval} วินาที"))
    Wait For Elements State    ${row}    visible
    Wait For Elements State    ${BROADCAST_ROW} >> nth=0    visible
    ${client_id}=    Get Text    ${row} td:first-child code
    Click    ${TIMED_TOGGLE}
    Wait For Elements State    ${TIMED} ${CLOSED}    visible
    Wait For Elements State    ${CLIENT_ROWS}:has(code:text-is("${client_id}"))    detached    timeout=15s
    Wait For Elements State    ${BROADCAST_ROW} >> nth=0    visible

*** Keywords ***
Start Test Browser
    ${url}=    Evaluate    $BASE_URL.rstrip("/")
    VAR    ${APP_URL}    ${url}    scope=SUITE
    New Browser    chromium    headless=${HEADLESS}    slowMo=${SLOW_MO}

Show Result And Close Context
    TRY
        ${headless}=    Convert To Boolean    ${HEADLESS}
        IF    not $headless
            Sleep    ${VIEW_DELAY}
        END
    FINALLY
        Close Context
    END

Open Receiver
    ${page}=    New Page    ${APP_URL}/
    Wait For Elements State    ${TIMED} ${OPEN}    visible
    Wait For Elements State    ${BROADCAST} ${OPEN}    visible
    Wait For Elements State    ${RECEIVED_AT}    visible
    RETURN    ${page}

Send Unique Broadcast
    Click    css=a[href="/broadcast"][target="_blank"]
    Switch Page    NEW
    Get Url    $=    /broadcast
    ${message}=    Evaluate    "SSE smoke " + str(uuid.uuid4())    modules=uuid
    Fill Text    css=#broadcast-message    ${message}
    Click    css=.broadcast-form button.primary
    RETURN    ${message}

Timed Stream Should Receive Another Event
    [Arguments]    ${previous}
    Wait For Elements State    ${TIMED} ${OPEN}    visible
    Get Attribute    ${RECEIVED_AT}    datetime    !=    ${previous}
