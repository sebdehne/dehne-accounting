import {formatLocalDayMonth, formatLocalDayMonthYear} from "../../utils/formatting";
import {useGlobalState} from "../../utils/globalstate";
import dayjs from "dayjs";


type DateViewerProps = {
    date: string | dayjs.Dayjs | undefined;
}
export const DateViewer = ({date}: DateViewerProps) => {
    const {userStateV2} = useGlobalState()

    const formatFn = userStateV2?.periodType === "all" ? formatLocalDayMonthYear : formatLocalDayMonth;

    if (!date) return null;

    return <div>
        {formatFn(dayjs(date))}
    </div>
}

