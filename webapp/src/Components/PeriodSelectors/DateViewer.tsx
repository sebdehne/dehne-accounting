import {formatLocalDayMonth, formatLocalDayMonthYear} from "../../utils/formatting";
import moment from "moment";
import {useGlobalState} from "../../utils/userstate";


type DateViewerProps = {
    date: string | moment.Moment | undefined;
}
export const DateViewer = ({date}: DateViewerProps) => {
    const {userStateV2} = useGlobalState()

    const formatFn = userStateV2?.periodType === "all" ? formatLocalDayMonthYear : formatLocalDayMonth;

    if (!date) return null;

    return <div>
        {formatFn(moment(date))}
    </div>
}

